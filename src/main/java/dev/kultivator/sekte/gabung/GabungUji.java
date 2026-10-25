package dev.kultivator.sekte.gabung;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The test tree of Gabung Sekte (DESIGN 41.13.3): {@code /kultivasi uji gabung ...}, op 2, through
 * {@code DuniaCommands.test}. The root and a few data subs live here; every module hangs its own subs with
 * {@link #daftar} (or {@code GabungApi.daftarUji}) from its init, so nobody edits this file for a new sub. The tree is
 * built when Brigadier asks, so later registrations count.
 * <p>
 * Built in: {@code uji gabung} (every chip of 41.13.3, clickable), {@code daftar} (every sect: class, tier, style,
 * alignment, the tester's standing, compound born, sky palace, player members), {@code kenal <sekte|semua>},
 * {@code terima <sekte> <pangkat>} (the data door only; 41B adds the short ceremony), {@code pangkat <pangkat>},
 * {@code pk <n>}, {@code ikatan <n>}, {@code reset [semua]}.
 */
public final class GabungUji {

	/** Chips of group {@code gabung_sekte} of {@code /kultivasi uji} (41.13.3); a module may append more. */
	public static final List<String> CHIPS = new CopyOnWriteArrayList<>(List.of(
			"gabung", "gabung daftar", "gabung sebaran [preset]", "gabung kelas <sekte> <1-9>",
			"gabung tp <sekte> [gerbang|aula|jasa|arena|puncak|ujian|kediaman <n>|renungan]", "gabung titik <sekte>",
			"gabung kenal <sekte|semua>", "gabung ujian <sekte> [tahap 1|2|3] [corak <id>]", "gabung ujian lulus", "gabung ujian gagal",
			"gabung terima <sekte> <pangkat>", "gabung sumpah", "gabung shifu [pilih <n>]", "gabung pangkat <pangkat>", "gabung pk <n>",
			"gabung ikatan <n>", "gabung misi [isi|selesai <id>]", "gabung tugas <id>", "gabung jatah", "gabung sewa lewat",
			"gabung kuota bagi", "gabung turnamen [mulai|babak lewati]", "gabung perang <lawan>", "gabung perang skor <n>",
			"gabung perang akhir", "gabung serbuan [live]", "gabung langgar <aturan>", "gabung kurung <detik>", "gabung sidang",
			"gabung pamit [restu]", "gabung usir", "gabung belot", "gabung pemburu", "gabung tamu", "gabung cabang",
			"gabung suksesi [bab 1|2|3]", "gabung ketua", "gabung serap", "gabung lebur", "gabung pisah", "gabung merosot",
			"gabung bangkit", "gabung dua_topeng", "gabung tokoh <sekte>", "gabung anggaran", "gabung reset [semua]"));

	private static final Map<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> SUBS = new LinkedHashMap<>();
	private static boolean registered;

	/** Sect ids of this world's plan, for suggestions. */
	public static final SuggestionProvider<CommandSourceStack> SEKTE = (ctx, builder) ->
			SharedSuggestionProvider.suggest(SekteApi.diplomasi().sects(), builder);
	private static final SuggestionProvider<CommandSourceStack> PANGKAT = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (Pangkat p : Pangkat.values()) {
			ids.add(p.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	/** From {@code Gabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		DuniaCommands.test("gabung", GabungUji::node);
	}

	/** Adds {@code uji gabung <sub>}; the node's literal should be {@code sub}. */
	public static synchronized void daftar(String sub, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>> node) {
		if (sub != null && node != null) {
			SUBS.put(sub + "#" + SUBS.size(), node);
		}
	}

	/** Appends chips (each starting with {@code "gabung "}). */
	public static void chips(String... chips) {
		if (chips != null) {
			for (String chip : chips) {
				if (!CHIPS.contains(chip)) {
					CHIPS.add(chip);
				}
			}
		}
	}

	/** The root node {@code gabung} with the built-in subs and every registered sub. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gabung")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("daftar").executes(ctx -> daftarSekte(ctx.getSource())))
				.then(Commands.literal("kenal")
						.then(Commands.literal("semua").executes(ctx -> kenal(ctx, null)))
						.then(Commands.argument("sekte", StringArgumentType.word()).suggests(SEKTE)
								.executes(ctx -> kenal(ctx, StringArgumentType.getString(ctx, "sekte")))))
				.then(Commands.literal("terima")
						.then(Commands.argument("sekte", StringArgumentType.word()).suggests(SEKTE)
								.then(Commands.argument("pangkat", StringArgumentType.word()).suggests(PANGKAT)
										.executes(ctx -> terima(ctx, StringArgumentType.getString(ctx, "sekte"),
												StringArgumentType.getString(ctx, "pangkat"))))))
				.then(Commands.literal("pangkat")
						.then(Commands.argument("pangkat", StringArgumentType.word()).suggests(PANGKAT)
								.executes(ctx -> pangkat(ctx, StringArgumentType.getString(ctx, "pangkat")))))
				.then(Commands.literal("pk")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 1_000_000))
								.executes(ctx -> pk(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("ikatan")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 100))
								.executes(ctx -> ikatan(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("reset")
						.executes(ctx -> reset(ctx, false))
						.then(Commands.literal("semua").executes(ctx -> reset(ctx, true))));
		for (Map.Entry<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> entry : List.copyOf(SUBS.entrySet())) {
			try {
				ArgumentBuilder<CommandSourceStack, ?> sub = entry.getValue().get();
				if (sub != null) {
					root.then(sub);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Gabung test command {} could not be built", entry.getKey(), exception);
			}
		}
		return root;
	}

	// ================================================================== built-in subs

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.gabung.uji.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		MutableComponent line = Component.literal(" ");
		int inLine = 0;
		for (String chip : CHIPS) {
			if (inLine > 0) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			int cut = chip.length();
			for (int i = 0; i < chip.length(); i++) {
				if (chip.charAt(i) == '<' || chip.charAt(i) == '[') {
					cut = i;
					break;
				}
			}
			String command = "/kultivasi uji " + chip.substring(0, cut).trim() + (cut < chip.length() ? " " : "");
			line.append(Component.literal(chip).withStyle(style -> style.withColor(ChatFormatting.AQUA)
					.withClickEvent(new ClickEvent.SuggestCommand(command))));
			if (++inLine >= 5) {
				MutableComponent done = line;
				source.sendSuccess(() -> done, false);
				line = Component.literal(" ");
				inLine = 0;
			}
		}
		if (inLine > 0) {
			MutableComponent done = line;
			source.sendSuccess(() -> done, false);
		}
		return 1;
	}

	/** {@code uji gabung daftar}: one line per sect of the plan. */
	private static int daftarSekte(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		DiplomasiService d = SekteApi.diplomasi();
		long seed = server.overworld().getSeed();
		GerbangPlan.Plan plan = GerbangPlan.of(seed);
		List<Integer> palaces = new ArrayList<>();
		for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(seed)) {
			palaces.add(istana.gate().index());
		}
		UUID tester = source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
		GabungData data = GabungData.get(server);
		source.sendSuccess(() -> Component.translatable("kultivator.gabung.uji.daftar.judul", plan.entries().size())
				.withStyle(ChatFormatting.GOLD), false);
		for (GerbangPlan.Entry e : plan.entries()) {
			String id = e.id();
			int tier = Math.max(1, d.tier(id));
			KelasSekte kelas = KelasSekte.dariTier(tier);
			Corak corak = Corak.of(e.kataA());
			Component name = d.name(id) != null ? d.name(id) : e.name();
			int standing = tester == null ? 0 : d.standing(tester, id);
			boolean kompleks = KompleksApi.spot(id, "aula") != null;
			boolean istana = palaces.contains(e.index());
			int members = data.anggotaSekte(id).size();
			Component line = Component.translatable("kultivator.gabung.uji.daftar.baris", id, name, kelas.nama(), tier, corak.nama(),
					Component.translatable("kultivator.gabung.aliran." + Math.max(0, d.aliran(id))), standing,
					Component.translatable(kompleks ? "kultivator.gabung.uji.ya" : "kultivator.gabung.uji.tidak"),
					Component.translatable(istana ? "kultivator.gabung.uji.ya" : "kultivator.gabung.uji.tidak"), members)
					.withColor(kelas.warna());
			source.sendSuccess(() -> line, false);
		}
		return plan.entries().size();
	}

	private static int kenal(CommandContext<CommandSourceStack> ctx, String sekte) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int n = 0;
		for (String id : SekteApi.diplomasi().sects()) {
			if (sekte == null || sekte.equals(id)) {
				if (GabungApi.kenal(player, id)) {
					n++;
				}
			}
		}
		int count = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.kenal", count), false);
		return Math.max(1, n);
	}

	private static int terima(CommandContext<CommandSourceStack> ctx, String sekte, String pangkatId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Pangkat pangkat = Pangkat.byId(pangkatId);
		if (pangkat == null || SekteApi.diplomasi().tier(sekte) <= 0) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.salah", sekte + " " + pangkatId));
			return 0;
		}
		Keanggotaan k = GabungApi.anggota(player);
		if (k.status.anggota()) {
			k.lepas();
			GabungData.get(ctx.getSource().getServer()).setDirty();
		}
		GabungApi.terima(player, sekte, pangkat, pangkat == Pangkat.TAMU ? Pintu.TAMU : Pintu.GERBANG);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.terima", pangkat.nama(),
				nama(sekte)), true);
		return 1;
	}

	private static int pangkat(CommandContext<CommandSourceStack> ctx, String pangkatId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Pangkat pangkat = Pangkat.byId(pangkatId);
		if (pangkat == null || !GabungApi.anggota(player).status.anggota()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
			return 0;
		}
		GabungApi.setPangkat(player, pangkat, "gabung.uji");
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.pangkat", pangkat.nama()), true);
		return 1;
	}

	private static int pk(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
			return 0;
		}
		// a test value is set as-is (no skalaPk), recorded and announced like every PK change
		int lama = k.pk;
		k.pk = n;
		if (n > lama) {
			k.pkTotal += n - lama;
		}
		k.catat(GabungApi.hari(ctx.getSource().getServer()), "kultivator.gabung.riwayat.pk_uji", Integer.toString(n));
		GabungData.get(ctx.getSource().getServer()).setDirty();
		GabungBus.PK_BERUBAH.invoker().onPkBerubah(player, k.sekte, lama, n, "gabung.uji");
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.pk", n), true);
		return 1;
	}

	private static int ikatan(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Keanggotaan k = GabungApi.anggota(player);
		k.ikatanShifu = n;
		GabungData.get(ctx.getSource().getServer()).setDirty();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.ikatan", n), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx, boolean semua) throws CommandSyntaxException {
		MinecraftServer server = ctx.getSource().getServer();
		GabungData data = GabungData.get(server);
		if (semua) {
			data.hapusSemua();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				GabungPayloads.siarkanLencana(p);
			}
		} else {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			data.hapus(player.getUUID());
			GabungPayloads.siarkanLencana(player);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable(semua ? "kultivator.gabung.uji.reset_semua" : "kultivator.gabung.uji.reset"), true);
		return 1;
	}

	private static Component nama(String sekte) {
		Component name = SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte) : name;
	}

	private GabungUji() {
	}
}

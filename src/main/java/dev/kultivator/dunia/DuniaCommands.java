package dev.kultivator.dunia;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The commands of DESIGN 34.4.6 (contract C34.S4). Modules hand their nodes to {@link #test} (op-only, under
 * {@code /kultivasi uji <literal>} and {@code /cultivate uji <literal>}; Brigadier merges them with the existing
 * {@code uji} node, so the old {@code debug} alias reaches them too) or {@link #player} (under {@code /kultivasi
 * <literal>}). Each node is built fresh per root by its supplier. Registered in a command phase after the default one,
 * so the main tree (and its {@code debug} redirect) exists before these merge into it.
 * <p>
 * Spine nodes: {@code uji jelajah} (this phase's grouped help), {@code uji jelajah muat} (reload the config),
 * {@code uji jelajah anchor} (anchors within 64 blocks), {@code uji struktur cari <id>} (find the nearest structure and
 * teleport, the W6 rule: never {@code /place}), {@code uji struktur lihat <folder/piece>} (a 20 s client ghost preview
 * of a template made of block displays; never a block).
 */
public final class DuniaCommands {

	private static final Identifier PHASE = Kultivator.id("dunia_commands");
	private static final Map<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> TESTS = new LinkedHashMap<>();
	private static final Map<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> PLAYERS = new LinkedHashMap<>();
	public static final int GHOST_TICKS = 400;
	public static final int GHOST_MAX_BOXES = 192;

	/**
	 * The chips of this phase (DESIGN 34.5 "Uji" lines), shown by {@code uji jelajah} and by the {@code jelajah} group of
	 * {@code /kultivasi uji}. Text up to the first argument placeholder is what a click puts into the chat box.
	 */
	public static final List<String> CHIPS = List.of(
			"jelajah", "jelajah muat", "jelajah anchor", "struktur cari <id>", "struktur lihat <folder/potongan>",
			"kota cari [tampilan]", "kota barisan", "kota obrol", "kota gerak <gerak>", "kota langit <peristiwa>", "kota npc <peran>", "kota lelang mulai", "kota lelang tutup", "kota lelang tawar <persen>", "kota teh <jenis>",
			"kota gelap buka", "kota gelap razia", "kota nama_baik <-100..100>", "kota misi baru", "kota misi selesai", "kota stok ulang",
			"pengembara [dao] [ranah] [watak]", "pengembara duel", "pengembara diskusi menang", "pengembara diskusi kalah", "pengembara hapus",
			"saingan daftar", "saingan datang <grup>", "saingan perlombaan", "saingan sergap", "saingan dendam <grup> <0-100>",
			"kota_abadi cari", "kota_abadi kuliah", "kota_abadi persik", "kota_abadi titah",
			"gua cari <kecil|agung|kuno>", "gua teka <jenis>", "gua selesai", "gua penjaga", "gua reset",
			"penjara cari", "penjara segel <0-100>", "penjara cabut <1-5>", "penjara lepas <raja>", "penjara segel_ulang", "penjara hapus",
			"menara cari", "menara masuk", "menara lantai <1-100>", "menara selesai", "menara gagal", "menara keluar", "menara rekor <n>",
			"langit meteor [menit]", "langit gerhana [menit]", "langit bulan_kembar [menit]", "langit berhenti", "langit jadwal", "langit jatuh",
			"harta_lahir [1-4] [jenis]", "harta_lahir dekat", "harta_lahir hapus", "harta_lahir jadwal",
			"cuaca <cerah|tipis|pekat|ungu> [menit]", "cuaca info",
			"naga <air|awan|petir> [lemah]", "naga tunduk menang", "naga tunduk kalah", "naga hapus", "naga tunggang", "naga info",
			"evolusi <4-6>", "evolusi tribulasi [lulus|gagal]", "evolusi garis <wolf|fox|jiao>", "evolusi info",
			"bawah masuk", "bawah pulang", "bawah meng_po <dao|hati|teknik>", "bawah pengadilan <1-10> [lulus]", "bawah cermin",
			"bawah coret", "bawah arwah <n>", "bawah kuil cari",
			"laut masuk", "laut pulang", "laut pulau cari <jenis>", "laut istana cari", "laut monster <jenis> [tingkat]", "laut penyu",
			"laut penglai", "laut pusaran cari",
			"kapal beri <perahu|sekte|bahtera>", "kapal panggil", "kapal bahan_bakar <menit>", "kapal lompat <x> <z>", "kapal bajak",
			"kapal rusak", "kapal perbaiki", "kapal hapus",
			"peta ungkap <radius>", "peta situs semua", "peta reset", "peta domain", "peta sampel",
			// DESIGN 38.D.10 (38W owns the chip list; each module registers its own node)
			"tempat rencana", "tempat cek", "tempat zona", "tempat kepadatan [radius]", "tempat ukur", "tempat lihat <folder/potongan>",
			"tempat rusak", "tempat perbaiki", "tempat tagihan", "tempat takut", "tempat cetak", "tempat sitaan", "tempat gugus",
			"kota_besar cari [indeks]", "kota_besar distrik <id>", "kota_besar lot", "kota_besar jam_malam [tutup|buka]",
			"kota_besar hukum <poin>", "kota_besar sel", "kota_besar acara <acara> [mulai|akhir]", "kota_besar alur <bab>",
			"kota_besar teleport <tujuan>", "kota_besar istana",
			"ekonomi senjata <bentuk> <mutu>", "ekonomi pesanan selesai", "ekonomi lencana <1-9>", "ekonomi misi <pangkat>",
			"ekonomi bank", "ekonomi lapak [hari]", "ekonomi kafilah [permintaan]", "ekonomi simulasi <pita> <hari>", "ekonomi simulasi semua <hari>", "ekonomi judi <n>",
			"ekonomi kas", "ekonomi kurs", "ekonomi stok ulang", "ekonomi lelang [agung|kota]",
			"hidup jadwal <npc>", "hidup jam <0-23>", "hidup klip <id> [npc]", "hidup klip semua", "hidup prop <id>", "hidup impostor",
			"hidup saksi", "hidup bahaya", "hidup cuaca <hujan|badai|kabut|gerhana>",
			"cerita dialog <id>", "cerita quest <id> [tahap]", "cerita bendera <id> [on|off]", "cerita rumor [tambah <templat>]", "cerita tokoh",
			"kampung cari <ukuran> [gaya]", "kampung klan", "kampung panen", "kampung anak",
			"kompleks cari <sekte>", "kompleks rotasi", "kompleks sikap <sekte> <sikap>",
			"tempat_gua cari <jenis>", "tempat_gua ujian <warisan|cermin|pertapa> [tahap]", "tempat_gua penjaga", "tempat_gua tikus [n]",
			"tempat_gua status",
			"jalan cari", "jalan peta", "jalan cek", "jalan pos <jenis>",
			"ui layar <id>", "ui skala <2|3|4>",
			// DESIGN 38.T.13 (themed cities, 38TK)
			"tema rencana [tema]", "tema putusan <tema>", "tema cari <tema> [indeks]", "tema distrik <tema> <id>", "tema lot", "tema cek",
			"tema acara <tema> <acara> [mulai|akhir]", "tema kapal", "tema perahu", "tema lift [naik|turun|macet|pulih]", "tema unta",
			"tema arena <peringkat>", "tema taruhan", "tema infami <0-100>", "tema gentar <0-100>", "tema buronan", "tema pemburu",
			"tema dagang [harga|batas|jenuh]", "tema dagang simulasi <hari>", "tema kontrak [buat|selesai|gagal]", "tema alur <tema> <bab>",
			"tema model", "tema fx <efek>", "tema kepadatan [radius]",
			// DESIGN 40.6 (shared appearance "Sosok", 40I)
			"penampilan galeri peran [halaman]", "penampilan galeri murid", "penampilan galeri jalan", "penampilan galeri usia <peran>",
			"penampilan galeri dao", "penampilan galeri fisik", "penampilan acak [n]", "penampilan klip <id> [radius]", "penampilan klip semua",
			"penampilan klip kelompok <kelompok>", "penampilan jalan", "penampilan wajah", "penampilan pegas", "penampilan murid <ubah>",
			"penampilan pratinjau_benih <benih> <rupa>", "penampilan hapus",
			// DESIGN 41.13.3 (Gabung Sekte, 41A; the whole list is group "gabung_sekte" of /kultivasi uji)
			"gabung", "gabung daftar", "gabung sebaran [preset]", "gabung tp <sekte> [titik]", "gabung ujian <sekte> [tahap 1|2|3] [corak <id>]",
			"gabung terima <sekte> <pangkat>", "gabung reset [semua]",
			// DESIGN 42.14.3 (Kota yang Hidup, 42A; the whole list is group "kota_hidup" of /kultivasi uji)
			"kota_hidup", "kota_hidup cek", "kota_hidup warga daftar [situs]", "kota_hidup cakap info <npc>", "kota_hidup sebaran peta",
			"kota_hidup ekonomi harga [kategori]", "kota_hidup acara musim <musim>", "kota_hidup tumbuh kavling [daftar]",
			"kota_hidup kompat periksa [radius]", "kota_hidup anggaran", "kota_hidup reset [bagian] [situs]");

	private static final SuggestionProvider<CommandSourceStack> STRUCTURES = (ctx, builder) -> {
		Registry<Structure> registry = ctx.getSource().getServer().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		return SharedSuggestionProvider.suggest(registry.keySet().stream().filter(id -> Kultivator.MOD_ID.equals(id.getNamespace()))
				.map(Identifier::getPath), builder);
	};

	public static final SuggestionProvider<CommandSourceStack> TEMPLATES = (ctx, builder) -> {
		List<String> paths = new ArrayList<>();
		ctx.getSource().getServer().getResourceManager().listResources("structure",
				id -> id.getNamespace().equals(Kultivator.MOD_ID) && id.getPath().endsWith(".nbt")).keySet()
				.forEach(id -> paths.add(id.getPath().substring("structure/".length(), id.getPath().length() - ".nbt".length())));
		return SharedSuggestionProvider.suggest(paths, builder);
	};

	static void init() {
		CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		CommandRegistrationCallback.EVENT.register(PHASE, (dispatcher, buildContext, selection) -> register(dispatcher));
		test("jelajah", DuniaCommands::jelajahNode);
		test("struktur", DuniaCommands::strukturNode);
	}

	/** An op-only test node under {@code uji <literal>} (the node checks the op level itself as well). */
	public static void test(String literal, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
		if (literal != null && node != null) {
			TESTS.put(literal + "#" + TESTS.size(), node);
		}
	}

	/** A player command under {@code /kultivasi <literal>} and {@code /cultivate <literal>}. */
	public static void player(String literal, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
		if (literal != null && node != null) {
			PLAYERS.put(literal + "#" + PLAYERS.size(), node);
		}
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String root : new String[]{"kultivasi", "cultivate"}) {
			LiteralArgumentBuilder<CommandSourceStack> uji = Commands.literal("uji").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
			for (Map.Entry<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> entry : TESTS.entrySet()) {
				try {
					LiteralArgumentBuilder<CommandSourceStack> node = entry.getValue().get();
					if (node != null) {
						uji.then(node.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)));
					}
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Dunia test command {} could not be built", entry.getKey(), exception);
				}
			}
			LiteralArgumentBuilder<CommandSourceStack> top = Commands.literal(root).then(uji);
			for (Map.Entry<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> entry : PLAYERS.entrySet()) {
				try {
					LiteralArgumentBuilder<CommandSourceStack> node = entry.getValue().get();
					if (node != null) {
						top.then(node);
					}
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Dunia player command {} could not be built", entry.getKey(), exception);
				}
			}
			dispatcher.register(top);
		}
	}

	// ================================================================== jelajah

	private static LiteralArgumentBuilder<CommandSourceStack> jelajahNode() {
		return Commands.literal("jelajah")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("muat").executes(ctx -> reload(ctx.getSource())))
				.then(Commands.literal("anchor").executes(DuniaCommands::anchors));
	}

	/** {@code uji jelajah}: the chips of this phase, clickable. */
	static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.dunia.cmd.help.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.dunia.cmd.help.intro").withStyle(ChatFormatting.GRAY), false);
		MutableComponent line = Component.literal(" ");
		int inLine = 0;
		for (String chip : CHIPS) {
			if (inLine > 0) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(chip(chip));
			if (++inLine >= 6) {
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

	private static Component chip(String chip) {
		int cut = chip.length();
		for (int i = 0; i < chip.length(); i++) {
			char c = chip.charAt(i);
			if (c == '<' || c == '[') {
				cut = i;
				break;
			}
		}
		String fixed = chip.substring(0, cut).trim();
		String command = "/kultivasi uji " + fixed + (cut < chip.length() ? " " : "");
		return Component.literal(chip).withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("/kultivasi uji " + chip))));
	}

	private static int reload(CommandSourceStack source) {
		boolean ok = DuniaConfig.reload();
		source.sendSuccess(() -> Component.translatable(ok ? "kultivator.dunia.cmd.reload.ok" : "kultivator.dunia.cmd.reload.fail",
				DuniaConfig.file().getFileName().toString()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		return ok ? 1 : 0;
	}

	private static int anchors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		List<DuniaAnchorEntity> near = new ArrayList<>();
		for (DuniaAnchorEntity anchor : DuniaAnchors.loaded(level, "")) {
			if (anchor.position().distanceToSqr(player.position()) <= 64.0D * 64.0D) {
				near.add(anchor);
			}
		}
		near.sort(java.util.Comparator.comparingDouble(anchor -> anchor.position().distanceToSqr(player.position())));
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dunia.cmd.anchor.head", near.size(), DuniaAnchors.count())
				.withStyle(ChatFormatting.GOLD), false);
		int shown = 0;
		for (DuniaAnchorEntity anchor : near) {
			if (shown++ >= 24) {
				break;
			}
			BlockPos at = anchor.blockPosition();
			String state = anchor.state().toString();
			if (state.length() > 80) {
				state = state.substring(0, 77) + "...";
			}
			String shownState = state;
			int distance = (int) Math.sqrt(anchor.position().distanceToSqr(player.position()));
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dunia.cmd.anchor.line", anchor.kind(), at.toShortString(),
					distance, anchor.protectRadius(), shownState).withStyle(ChatFormatting.GRAY), false);
		}
		return near.size();
	}

	// ================================================================== struktur

	private static LiteralArgumentBuilder<CommandSourceStack> strukturNode() {
		return Commands.literal("struktur")
				.then(Commands.literal("cari")
						.then(Commands.argument("id", StringArgumentType.greedyString()).suggests(STRUCTURES)
								.executes(ctx -> find(ctx, StringArgumentType.getString(ctx, "id")))))
				.then(Commands.literal("lihat")
						.executes(ctx -> clearGhost(ctx))
						.then(Commands.argument("potongan", StringArgumentType.greedyString()).suggests(TEMPLATES)
								.executes(ctx -> ghost(ctx, StringArgumentType.getString(ctx, "potongan")))));
	}

	/** {@code uji struktur cari <id>}: vanilla locate within 100 chunks, then a teleport onto the surface. */
	private static int find(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		return findAndTeleport(player, id.contains(":") ? Identifier.tryParse(id) : Kultivator.id(id));
	}

	/**
	 * Finds the nearest structure {@code id} from the player (vanilla locate, 100 chunks) and teleports them onto its
	 * surface (the same as {@code /locate} + {@code /tp}: the chunk loads like any teleport). Modules' {@code ... cari}
	 * test nodes call this. Returns 1 on success.
	 */
	public static int findAndTeleport(ServerPlayer player, Identifier id) {
		ServerLevel level = (ServerLevel) player.level();
		if (id == null) {
			player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.unknown", "?").withStyle(ChatFormatting.RED));
			return 0;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Optional<Holder.Reference<Structure>> holder = registry.get(id);
		if (holder.isEmpty()) {
			player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.unknown", id.toString()).withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos found = level.findNearestMapStructure(HolderSet.direct(holder.get()), player.blockPosition(), 100, false);
		if (found == null) {
			player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.none", id.toString()).withStyle(ChatFormatting.RED));
			return 0;
		}
		level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.FULL, true);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
		if (y <= level.getMinY() + 1) {
			y = Mth.clamp(found.getY(), level.getMinY() + 2, level.getMaxY() - 2);
		}
		int distance = (int) Math.sqrt(found.distSqr(player.blockPosition()));
		player.teleportTo(level, found.getX() + 0.5D, y + 1.0D, found.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot(), true);
		player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.ok", id.toString(), found.getX(), y, found.getZ(), distance)
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int clearGhost(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Net.send(player, new DuniaGhostPayload(0.0D, 0.0D, 0.0D, 0, new int[0]));
		return 1;
	}

	/**
	 * The ghost preview of template {@code path} ({@code <folder/piece>}) in front of the command's player (DESIGN 38:
	 * {@code uji tempat lihat} uses it too). 0 when the template is missing.
	 */
	public static int lihat(CommandContext<CommandSourceStack> ctx, String path) throws CommandSyntaxException {
		return ghost(ctx, path);
	}

	/** {@code uji struktur lihat <folder/piece>}: the template's blocks merged into boxes, drawn by the client. */
	private static int ghost(CommandContext<CommandSourceStack> ctx, String path) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Identifier file = Kultivator.id("structure/" + path + ".nbt");
		Optional<Resource> resource = player.level().getServer().getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dunia.cmd.ghost.missing", path));
			return 0;
		}
		CompoundTag root;
		try (InputStream in = resource.get().open()) {
			root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		} catch (Exception exception) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dunia.cmd.ghost.missing", path));
			return 0;
		}
		int[] boxes = boxes(player.level().registryAccess().lookupOrThrow(Registries.BLOCK), root);
		Vec3 look = player.getLookAngle();
		Vec3 base = player.position().add(look.x * 6.0D, 0.0D, look.z * 6.0D);
		Net.send(player, new DuniaGhostPayload(Math.floor(base.x), Math.floor(base.y), Math.floor(base.z), GHOST_TICKS, boxes));
		int count = boxes.length / DuniaGhostPayload.INTS_PER_BOX;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dunia.cmd.ghost.ok", path, count, GHOST_TICKS / 20)
				.withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	/**
	 * Merges the template's non-air blocks into boxes of one state (x runs, then rows along z, then layers along y) and
	 * keeps the {@link #GHOST_MAX_BOXES} largest.
	 */
	private static int[] boxes(net.minecraft.core.HolderGetter<Block> blocks, CompoundTag root) {
		ListTag size = root.getListOrEmpty("size");
		int sx = Mth.clamp(size.getIntOr(0, 0), 0, 64);
		int sy = Mth.clamp(size.getIntOr(1, 0), 0, 128);
		int sz = Mth.clamp(size.getIntOr(2, 0), 0, 64);
		if (sx == 0 || sy == 0 || sz == 0) {
			return new int[0];
		}
		ListTag paletteTag = root.getListOrEmpty("palette");
		if (paletteTag.isEmpty()) {
			ListTag palettes = root.getListOrEmpty("palettes");
			if (!palettes.isEmpty() && palettes.get(0) instanceof ListTag first) {
				paletteTag = first;
			}
		}
		int[] palette = new int[paletteTag.size()];
		for (int i = 0; i < paletteTag.size(); i++) {
			BlockState state = paletteTag.get(i) instanceof CompoundTag entry ? NbtUtils.readBlockState(blocks, entry)
					: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			palette[i] = state.isAir() ? -1 : Block.getId(state);
		}
		int[] grid = new int[sx * sy * sz];
		java.util.Arrays.fill(grid, -1);
		for (Tag each : root.getListOrEmpty("blocks")) {
			if (!(each instanceof CompoundTag block)) {
				continue;
			}
			ListTag pos = block.getListOrEmpty("pos");
			int x = pos.getIntOr(0, -1);
			int y = pos.getIntOr(1, -1);
			int z = pos.getIntOr(2, -1);
			int state = block.getIntOr("state", -1);
			if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || state < 0 || state >= palette.length) {
				continue;
			}
			grid[(y * sz + z) * sx + x] = palette[state];
		}
		boolean[] used = new boolean[grid.length];
		List<int[]> found = new ArrayList<>();
		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					int index = (y * sz + z) * sx + x;
					int state = grid[index];
					if (state < 0 || used[index]) {
						continue;
					}
					int w = 1;
					while (x + w < sx && grid[index + w] == state && !used[index + w]) {
						w++;
					}
					int d = 1;
					grow:
					while (z + d < sz) {
						for (int i = 0; i < w; i++) {
							int at = (y * sz + z + d) * sx + x + i;
							if (grid[at] != state || used[at]) {
								break grow;
							}
						}
						d++;
					}
					int h = 1;
					rise:
					while (y + h < sy) {
						for (int k = 0; k < d; k++) {
							for (int i = 0; i < w; i++) {
								int at = ((y + h) * sz + z + k) * sx + x + i;
								if (grid[at] != state || used[at]) {
									break rise;
								}
							}
						}
						h++;
					}
					for (int j = 0; j < h; j++) {
						for (int k = 0; k < d; k++) {
							for (int i = 0; i < w; i++) {
								used[((y + j) * sz + z + k) * sx + x + i] = true;
							}
						}
					}
					found.add(new int[]{x, y, z, w, h, d, state});
				}
			}
		}
		found.sort((a, b) -> Integer.compare(b[3] * b[4] * b[5], a[3] * a[4] * a[5]));
		int n = Math.min(GHOST_MAX_BOXES, found.size());
		int[] out = new int[n * DuniaGhostPayload.INTS_PER_BOX];
		for (int i = 0; i < n; i++) {
			System.arraycopy(found.get(i), 0, out, i * DuniaGhostPayload.INTS_PER_BOX, DuniaGhostPayload.INTS_PER_BOX);
		}
		return out;
	}

	private DuniaCommands() {
	}
}

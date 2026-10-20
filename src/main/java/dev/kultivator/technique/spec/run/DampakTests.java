package dev.kultivator.technique.spec.run;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.fx.Fx;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.kehancuran.Shapes;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * {@code /kultivasi uji dampak ...} (DESIGN 28.15, op only): showcase casts of the Phase 10 storyboard rows, test debris
 * and lift batches that change nothing, a real Kawah Lebur bowl under the player's own Mode Kehancuran switch, a test
 * sigil climax and the STOP packet. Registered by {@link TechniqueTests#addTestCommands}.
 */
public final class DampakTests {

	private static final String K = "kultivator.dampak_server.test.";
	private static final int GOLD = 0xF2C14E;
	private static final int MUTED = 0xA8B5B0;
	private static final int REFUSE = 0xFF8A7A;
	/** {@code uji dampak semua}: one row every 14 s. */
	private static final int ALL_GAP_TICKS = 280;
	/** Test batches: at most 256 entries, split into 64-entry packets. */
	private static final int MAX_TEST = 256;
	/** Test job ids start here (real jobs count up from 1). */
	private static final int TEST_JOB_BASE = 1_000_000;
	private static final double RANGE = 96.0D;

	private static int nextTestJob = TEST_JOB_BASE;

	/** One running {@code uji dampak semua}. */
	private static final class Tour {
		final List<TechniqueSpec> list;
		final ResourceKey<Level> dimension;
		int index;
		long next;

		Tour(List<TechniqueSpec> list, ResourceKey<Level> dimension, long next) {
			this.list = list;
			this.dimension = dimension;
			this.next = next;
		}
	}

	private static final Map<UUID, Tour> TOURS = new HashMap<>();

	private static final SuggestionProvider<CommandSourceStack> ROWS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.concat(Stream.of("semua"), rows().stream().map(TechniqueSpec::id)), builder);
	private static final SuggestionProvider<CommandSourceStack> PALETTES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Dampak.Palette.values()).filter(p -> p != Dampak.Palette.NONE)
					.map(p -> p.name().toLowerCase(Locale.ROOT)), builder);
	private static final SuggestionProvider<CommandSourceStack> PATTERNS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(SigilPattern.values()).filter(p -> p != SigilPattern.NONE)
					.map(p -> p.name().toLowerCase(Locale.ROOT)), builder);

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DampakTests::tick);
		ServerPlayerEvents.LEAVE.register(player -> TOURS.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOURS.clear());
	}

	/** Adds {@code dampak ...} under the op-only {@code uji} node. */
	static void addCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("dampak")
				.then(Commands.literal("semua").executes(ctx -> tour(self(ctx))))
				.then(Commands.literal("puing")
						.executes(ctx -> testBatch(self(ctx), 64, false))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_TEST))
								.executes(ctx -> testBatch(self(ctx), IntegerArgumentType.getInteger(ctx, "n"), false))))
				.then(Commands.literal("angkat")
						.executes(ctx -> testBatch(self(ctx), 24, true))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_TEST))
								.executes(ctx -> testBatch(self(ctx), IntegerArgumentType.getInteger(ctx, "n"), true))))
				.then(Commands.literal("kawah")
						.then(Commands.argument("palet", StringArgumentType.word()).suggests(PALETTES)
								.executes(ctx -> crater(self(ctx), StringArgumentType.getString(ctx, "palet")))))
				.then(Commands.literal("sigil")
						.then(Commands.argument("pola", StringArgumentType.word()).suggests(PATTERNS)
								.executes(ctx -> sigil(self(ctx), StringArgumentType.getString(ctx, "pola"), -1))
								.then(Commands.argument("radius", IntegerArgumentType.integer(2, 128))
										.executes(ctx -> sigil(self(ctx), StringArgumentType.getString(ctx, "pola"),
												IntegerArgumentType.getInteger(ctx, "radius"))))))
				.then(Commands.literal("berhenti").executes(ctx -> stop(self(ctx))))
				.then(Commands.argument("id", StringArgumentType.word()).suggests(ROWS)
						.executes(ctx -> castRow(self(ctx), StringArgumentType.getString(ctx, "id")))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	/** Every spec with a Dampak row, in registry order (the rows of 28.12). */
	static List<TechniqueSpec> rows() {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.dampak() != null && spec.playerVisible()) {
				list.add(spec);
			}
		}
		return list;
	}

	// ================================================================== showcase casts

	private static int castRow(ServerPlayer player, String id) {
		if ("semua".equals(id)) {
			return tour(player);
		}
		TechniqueSpec spec = TechniqueRegistry.byId(id.toLowerCase(Locale.ROOT));
		if (spec == null || spec.dampak() == null) {
			Msg.chat(player, Component.translatable(K + "unknown", id).withColor(REFUSE));
			return 0;
		}
		return showcase(player, spec) ? 1 : 0;
	}

	/**
	 * One showcase cast through the {@code uji teknik pamer} runner (gates bypassed, free, forced visual): Mode Kehancuran
	 * carves only with {@code uji teknik kehancuran nyala} and the player's own switch.
	 */
	private static boolean showcase(ServerPlayer player, TechniqueSpec spec) {
		TechniqueRunner.endAll(player, EndReason.REPLACED);
		Forms.end(player, EndReason.REPLACED);
		Qi.fill(player);
		Dampak dampak = spec.dampak();
		Msg.title(player, spec.displayName().copy().withColor(spec.grade().ribbon()), spec.source().copy().withColor(0xC9B98F), 5, 60, 10);
		if (dampak != null) {
			player.sendSystemMessage(Component.translatable(K + "row", spec.displayName(), lower(dampak.mark()), lower(dampak.ride()),
					lower(dampak.vista()), dampak.climaxAt(), dampak.silence(), lower(dampak.debris()), lower(dampak.palette()),
					lower(dampak.sigil()), dampak.monument() == null ? "-" : dampak.monument()).withColor(MUTED));
		}
		try {
			boolean cast = TechniqueRunner.cast(player, spec, CastOptions.SHOWCASE);
			if (!cast) {
				Msg.chat(player, Component.translatable(K + "refused", spec.displayName()).withColor(REFUSE));
			}
			return cast;
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: uji dampak cast of {} failed", spec.id(), exception);
			Msg.chat(player, Component.translatable(K + "refused", spec.displayName()).withColor(REFUSE));
			return false;
		}
	}

	private static String lower(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	/** {@code uji dampak semua}: every row in turn, 14 s apart. */
	private static int tour(ServerPlayer player) {
		List<TechniqueSpec> list = rows();
		if (list.isEmpty()) {
			Msg.chat(player, Component.translatable(K + "none").withColor(REFUSE));
			return 0;
		}
		TOURS.put(player.getUUID(), new Tour(list, player.level().dimension(), player.level().getGameTime() + 10L));
		Msg.chat(player, Component.translatable(K + "tour.start", list.size(), ALL_GAP_TICKS / 20).withColor(GOLD));
		return list.size();
	}

	private static void tick(MinecraftServer server) {
		if (TOURS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Tour> entry : List.copyOf(TOURS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Tour tour = entry.getValue();
			if (player == null || player.isDeadOrDying() || !player.level().dimension().equals(tour.dimension)) {
				TOURS.remove(entry.getKey());
				continue;
			}
			long now = player.level().getGameTime();
			if (now < tour.next) {
				continue;
			}
			if (tour.index >= tour.list.size()) {
				TOURS.remove(entry.getKey());
				Msg.chat(player, Component.translatable(K + "tour.done", tour.list.size()).withColor(GOLD));
				continue;
			}
			TechniqueSpec spec = tour.list.get(tour.index++);
			tour.next = now + ALL_GAP_TICKS;
			Msg.actionBar(player, Component.literal(tour.index + "/" + tour.list.size() + "  ").withColor(GOLD)
					.append(spec.displayName().copy().withColor(spec.grade().ribbon())));
			showcase(player, spec);
		}
	}

	// ================================================================== test batches (nothing changes)

	/** A TEST batch of {@code n} real states sampled around the aim (LIFT flag for {@code angkat}); nothing changes. */
	private static int testBatch(ServerPlayer player, int n, boolean lift) {
		ServerLevel level = player.level();
		Vec3 aim = PlayerCaster.aimPoint(player);
		BlockPos centre = BlockPos.containing(aim);
		RandomSource random = level.getRandom();
		int radius = Math.max(3, (int) Math.ceil(Math.sqrt(n) / 1.5D));
		List<int[]> entries = new ArrayList<>();
		int tries = n * 4;
		while (entries.size() < Math.min(MAX_TEST, n) && tries-- > 0) {
			int dx = random.nextInt(radius * 2 + 1) - radius;
			int dz = random.nextInt(radius * 2 + 1) - radius;
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}
			int x = centre.getX() + dx;
			int z = centre.getZ() + dz;
			BlockPos column = new BlockPos(x, level.getMinY(), z);
			if (!level.isLoaded(column)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1 - random.nextInt(2);
			BlockPos pos = new BlockPos(x, top, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				continue;
			}
			entries.add(new int[]{Payloads.DampakDebris.pack(x - centre.getX(), top - centre.getY(), z - centre.getZ()), Block.getId(state)});
		}
		if (entries.isEmpty()) {
			Msg.chat(player, Component.translatable(K + "debris.empty").withColor(REFUSE));
			return 0;
		}
		int job = nextTestJob++;
		int flags = Payloads.DampakDebris.FLAG_SELF | (lift ? Payloads.DampakDebris.FLAG_LIFT : 0);
		Vec3 base = Vec3.atCenterOf(centre);
		int packets = 0;
		for (int from = 0; from < entries.size(); from += Payloads.DampakDebris.MAX) {
			int to = Math.min(entries.size(), from + Payloads.DampakDebris.MAX);
			int[] offsets = new int[to - from];
			int[] states = new int[to - from];
			for (int i = from; i < to; i++) {
				offsets[i - from] = entries.get(i)[0];
				states[i - from] = entries.get(i)[1];
			}
			broadcast(level, base, new Payloads.DampakDebris(job, Payloads.DampakDebris.TEST, player.getId(), -1, base.x, base.y, base.z,
					(short) entries.size(), (short) flags, offsets, states));
			packets++;
		}
		Msg.chat(player, Component.translatable(lift ? K + "lift" : K + "debris", entries.size(), packets).withColor(GOLD));
		return entries.size();
	}

	// ================================================================== a real crater (Mode Kehancuran)

	/** {@code uji dampak kawah <palet>}: a real r 4 bowl at the aim through {@code Kehancuran.submitSelf} with that palette. */
	private static int crater(ServerPlayer player, String name) {
		Dampak.Palette palette = null;
		for (Dampak.Palette value : Dampak.Palette.values()) {
			if (value != Dampak.Palette.NONE && value.name().equalsIgnoreCase(name)) {
				palette = value;
			}
		}
		if (palette == null) {
			Msg.chat(player, Component.translatable(K + "palette.unknown", name).withColor(REFUSE));
			return 0;
		}
		if (!Kehancuran.selfOn(player)) {
			Msg.chat(player, Component.translatable(K + "kawah.refused").withColor(REFUSE));
			return 0;
		}
		Vec3 aim = PlayerCaster.aimPoint(player);
		Vec3 look = player.getViewVector(1.0F);
		int cap = Math.min(512, TechniquePower.blockCap(Cultivators.get(player)));
		Kehancuran.SelfResult result = Kehancuran.submitSelf(player, "uji_dampak",
				List.of(new Shapes.Placed(Shapes.Spec.bowl(4), BlockPos.containing(aim), new Vec3(look.x, 0.0D, look.z))), cap, palette);
		Msg.chat(player, Component.translatable(K + "kawah.result", Component.translatable("kultivator.dampak_server.palette." + lower(palette)),
				lower(result)).withColor(result == Kehancuran.SelfResult.QUEUED || result == Kehancuran.SelfResult.DEFERRED ? GOLD : REFUSE));
		return result == Kehancuran.SelfResult.QUEUED || result == Kehancuran.SelfResult.DEFERRED ? 1 : 0;
	}

	// ================================================================== a test sigil

	/**
	 * {@code uji dampak sigil <pola> [radius]}: a CAST and a climax event (FLAG_SHOWCASE | FLAG_CLIMAX) of the first row that
	 * uses the pattern, at the aim, so every client near draws that row's sigil (radius in the climax's {@code radius});
	 * no trench, no damage, nothing carved.
	 */
	private static int sigil(ServerPlayer player, String name, int radius) {
		SigilPattern pattern = null;
		for (SigilPattern value : SigilPattern.values()) {
			if (value != SigilPattern.NONE && value.name().equalsIgnoreCase(name)) {
				pattern = value;
			}
		}
		if (pattern == null) {
			Msg.chat(player, Component.translatable(K + "pattern.unknown", name).withColor(REFUSE));
			return 0;
		}
		TechniqueSpec spec = null;
		for (TechniqueSpec each : rows()) {
			Dampak dampak = each.dampak();
			if (dampak != null && dampak.sigil() == pattern) {
				spec = each;
				break;
			}
		}
		if (spec == null || spec.dampak() == null) {
			Msg.chat(player, Component.translatable(K + "sigil.none", lower(pattern)).withColor(REFUSE));
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 aim = PlayerCaster.aimPoint(player);
		Vec3 from = player.position();
		float r = radius > 0 ? radius : spec.dampak().sigilRadius();
		int seed = level.getRandom().nextInt();
		short flags = (short) Payloads.TechFx.FLAG_SHOWCASE;
		byte tier = (byte) (TechniquePower.casterTier(Cultivators.get(player)) & 7);
		Fx.sendTech(level, new Payloads.TechFx(spec.netId(), (byte) Phase.CAST.ordinal(), from.x, from.y, from.z, aim.x, aim.y, aim.z, r,
				(float) from.distanceTo(aim), tier, flags, (short) 1, seed, player.getId(), (short) 20), RANGE);
		Phase climax = spec.climaxPhase();
		Fx.sendTech(level, new Payloads.TechFx(spec.netId(), (byte) climax.ordinal(), aim.x, aim.y, aim.z, aim.x, aim.y, aim.z, r, 0.0F, tier,
				(short) (Payloads.TechFx.FLAG_SHOWCASE | Payloads.TechFx.FLAG_CLIMAX), (short) 1, seed, player.getId(), (short) 0), RANGE);
		Msg.chat(player, Component.translatable(K + "sigil", Component.translatable("kultivator.dampak_server.sigil." + lower(pattern)),
				spec.displayName(), String.format(Locale.ROOT, "%.0f", r)).withColor(GOLD));
		return 1;
	}

	// ================================================================== stop

	/** {@code uji dampak berhenti}: every client near clears the Phase 10 visuals (STOP, job -1). */
	private static int stop(ServerPlayer player) {
		TOURS.remove(player.getUUID());
		Vec3 at = player.position();
		broadcast(player.level(), at, new Payloads.DampakDebris(-1, Payloads.DampakDebris.STOP, player.getId(), -1, at.x, at.y, at.z, (short) 0,
				(short) 0, new int[0], new int[0]));
		Msg.chat(player, Component.translatable(K + "stop").withColor(MUTED));
		return 1;
	}

	private static void broadcast(ServerLevel level, Vec3 near, Payloads.DampakDebris payload) {
		for (ServerPlayer each : level.players()) {
			if (each.distanceToSqr(near) <= RANGE * RANGE) {
				Net.send(each, payload);
			}
		}
	}

	/** The Sumber line key of a pattern (for the codex and the test chat). */
	public static @Nullable String sourceKey(SigilPattern pattern) {
		return pattern == SigilPattern.NONE ? null : "kultivator.dampak_server.sigil." + lower(pattern) + ".source";
	}

	private DampakTests() {
	}
}

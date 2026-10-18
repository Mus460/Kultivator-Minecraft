package dev.kultivator.formasi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreStore;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.IntruderSnapshot;
import dev.kultivator.formasi.core.Verifier;
import dev.kultivator.formasi.core.VirtualCores;
import dev.kultivator.formasi.fuel.Kas;
import dev.kultivator.formasi.fuel.Upkeep;
import dev.kultivator.formasi.integrate.SectBuildBridge;
import dev.kultivator.formasi.keeper.KeeperOrder;
import dev.kultivator.formasi.keeper.Keepers;
import dev.kultivator.formasi.match.ChunkReader;
import dev.kultivator.formasi.match.MatchResult;
import dev.kultivator.formasi.match.PatternMatcher;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Pattern;
import dev.kultivator.formasi.spec.PatternBlock;
import dev.kultivator.formasi.spec.Slot;
import dev.kultivator.formasi.stack.FormationField;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.build.api.FormasiAnchor;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The {@code uji formasi ...} test commands and {@code debug formasi ...} commands of module A (DESIGN 24.10). No test
 * command places or breaks a block: virtual cores are records only, and everything else reads or sets numbers.
 */
public final class FormasiTests {

	/** Ring radius of {@code uji formasi semua}. */
	public static final double RING_RADIUS = 14.0D;
	/** Slot states per line of {@code uji formasi status}. */
	private static final int STATUS_LINE = 40;
	private static final String[] DIRECTIONS = {"utara", "timur", "selatan", "barat"};
	private static final String[] DIRECTIONS_EN = {"north", "east", "south", "west"};

	private static final SuggestionProvider<CommandSourceStack> SPECS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(FormationRegistry.all().stream().filter(spec -> !spec.has(Flag.LEGACY_MIRROR)).map(FormationSpec::id), builder);
	private static final SuggestionProvider<CommandSourceStack> ARAH = (ctx, builder) -> SharedSuggestionProvider.suggest(DIRECTIONS, builder);
	private static final SuggestionProvider<CommandSourceStack> ORDERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(KeeperOrder.values()).map(KeeperOrder::id), builder);
	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Realm.values()).map(Realm::id), builder);
	private static final SuggestionProvider<CommandSourceStack> DAOS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Dao.values()).map(Dao::id), builder);
	private static final SuggestionProvider<CommandSourceStack> HOURS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(new String[]{"penuh", "1", "12", "48"}, builder);
	private static final SuggestionProvider<CommandSourceStack> TARGETS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(new String[]{"semua"}, builder);

	/** Adds module A's test commands to {@code uji formasi}. */
	public static void add(LiteralArgumentBuilder<CommandSourceStack> node) {
		node.then(Commands.literal("pasang").then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
				.executes(ctx -> place(ctx, VirtualCores.DEFAULT_MINUTES, false))
				.then(Commands.literal("paksa").executes(ctx -> place(ctx, VirtualCores.DEFAULT_MINUTES, true)))
				.then(Commands.argument("menit", IntegerArgumentType.integer(1, 120))
						.executes(ctx -> place(ctx, IntegerArgumentType.getInteger(ctx, "menit"), false))
						.then(Commands.literal("paksa").executes(ctx -> place(ctx, IntegerArgumentType.getInteger(ctx, "menit"), true))))));
		node.then(Commands.literal("pola").then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
				.executes(ctx -> hologram(ctx, -1))
				.then(Commands.argument("arah", StringArgumentType.word()).suggests(ARAH)
						.executes(ctx -> hologram(ctx, direction(StringArgumentType.getString(ctx, "arah")))))));
		node.then(Commands.literal("semua").executes(FormasiTests::ring));
		node.then(Commands.literal("daftar").executes(FormasiTests::list));
		node.then(Commands.literal("audit").executes(FormasiTests::audit));
		node.then(Commands.literal("status").executes(FormasiTests::status));
		node.then(Commands.literal("tumpuk").executes(FormasiTests::stack));
		node.then(Commands.literal("daya").then(Commands.argument("jam", StringArgumentType.word()).suggests(HOURS)
				.executes(ctx -> daya(ctx, null))
				.then(Commands.argument("target", StringArgumentType.word()).suggests(TARGETS)
						.executes(ctx -> daya(ctx, StringArgumentType.getString(ctx, "target"))))));
		node.then(Commands.literal("kas").then(Commands.argument("n", IntegerArgumentType.integer(0, (int) Kas.STONE_CAP))
				.executes(ctx -> kas(ctx, null))
				.then(Commands.literal("batu").executes(ctx -> kas(ctx, Boolean.TRUE)))
				.then(Commands.literal("kecubung").executes(ctx -> kas(ctx, Boolean.FALSE)))));
		node.then(Commands.literal("aula").then(Commands.argument("tingkat", IntegerArgumentType.integer(0, 9)).executes(FormasiTests::hall)));
		node.then(Commands.literal("penjaga")
				.then(Commands.literal("murid").executes(ctx -> keeper(ctx, KeeperOrder.JAGA))
						.then(Commands.argument("order", StringArgumentType.word()).suggests(ORDERS).executes(ctx -> {
							KeeperOrder order = KeeperOrder.byId(StringArgumentType.getString(ctx, "order"));
							return keeper(ctx, order == null ? KeeperOrder.JAGA : order);
						})))
				.then(Commands.literal("semu").then(Commands.argument("ranah", StringArgumentType.word()).suggests(REALMS)
						.executes(ctx -> pretend(ctx, 2, null))
						.then(Commands.argument("jenjang", IntegerArgumentType.integer(0, 5))
								.executes(ctx -> pretend(ctx, IntegerArgumentType.getInteger(ctx, "jenjang"), null))
								.then(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS)
										.executes(ctx -> pretend(ctx, IntegerArgumentType.getInteger(ctx, "jenjang"),
												StringArgumentType.getString(ctx, "dao"))))))));
		node.then(Commands.literal("tribulasi").executes(FormasiTests::tribulation));
		node.then(Commands.literal("hapus").executes(ctx -> remove(ctx, false, false))
				.then(Commands.literal("semua").executes(ctx -> remove(ctx, true, false))
						.then(Commands.literal("nyata").executes(ctx -> remove(ctx, true, true)))));
		node.then(Commands.literal("kinerja").executes(FormasiTests::performance));
	}

	/** Adds {@code debug formasi stats|verify|clear}. */
	public static void addDebug(LiteralArgumentBuilder<CommandSourceStack> debug) {
		debug.then(Commands.literal("formasi")
				.then(Commands.literal("stats").executes(FormasiTests::performance))
				.then(Commands.literal("verify").executes(FormasiTests::verify))
				.then(Commands.literal("clear").executes(FormasiTests::clearDimension)));
	}

	// ================================================================== helpers

	private static int direction(String text) {
		for (int i = 0; i < DIRECTIONS.length; i++) {
			if (DIRECTIONS[i].equalsIgnoreCase(text) || DIRECTIONS_EN[i].equalsIgnoreCase(text)) {
				return i;
			}
		}
		return -1;
	}

	private static @Nullable FormationSpec spec(ServerPlayer player, String id) {
		FormationSpec spec = FormationRegistry.byId(id);
		if (spec == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.unknown", id).withStyle(ChatFormatting.RED));
		}
		return spec;
	}

	private static void say(ServerPlayer player, String key, ChatFormatting color, Object... args) {
		Msg.chat(player, Component.translatable(key, args).withStyle(color));
	}

	/** The looked-at core, else the nearest core of any kind within 64 blocks. */
	private static @Nullable CoreRecord nearestCore(ServerPlayer player) {
		CoreRecord looked = Formasi.lookedAtCore(player);
		if (looked != null || !(player.level() instanceof ServerLevel level)) {
			return looked;
		}
		CoreRecord best = null;
		double bestDistance = 64.0D * 64.0D;
		for (CoreRecord record : CoreTicker.store(level).all()) {
			double distance = Vec3.atCenterOf(record.eye).distanceToSqr(player.position());
			if (distance < bestDistance) {
				best = record;
				bestDistance = distance;
			}
		}
		if (best == null) {
			say(player, "kultivator.formasi.uji.no_core", ChatFormatting.YELLOW);
		}
		return best;
	}

	private static Component name(CoreRecord record) {
		FormationSpec spec = FormationRegistry.byId(record.spec);
		return spec == null ? Component.literal(record.spec) : spec.name();
	}

	// ================================================================== virtual cores

	private static int place(CommandContext<CommandSourceStack> ctx, int minutes, boolean forced) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		FormationSpec spec = spec(player, StringArgumentType.getString(ctx, "id"));
		if (spec == null) {
			return 0;
		}
		if (spec.has(Flag.LEGACY_MIRROR)) {
			say(player, "kultivator.formasi.uji.legacy", ChatFormatting.YELLOW, spec.name());
			return 0;
		}
		ServerLevel level = (ServerLevel) player.level();
		CoreRecord record = VirtualCores.place(player, level, spec, player.blockPosition(), minutes, forced);
		Vec3 centre = Vec3.atCenterOf(record.eye);
		Fx.send(level, centre, FxType.FORMASI, FormasiFxIds.AWAKEN, spec.netId(), (float) record.radius);
		MutableComponent line = Component.translatable("kultivator.formasi.uji.placed", spec.name(), record.state.label(), minutes,
				Formasi.fmt(record.radius));
		if (record.reason != null) {
			line.append(Component.literal(" — ")).append(record.reason);
		}
		Msg.chat(player, line.withStyle(record.state.running() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		return 1;
	}

	private static int hologram(CommandContext<CommandSourceStack> ctx, int rot) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		FormationSpec spec = spec(player, StringArgumentType.getString(ctx, "id"));
		if (spec == null) {
			return 0;
		}
		Formasi.showHologram(player, spec, rot, Formasi.DEFAULT_HOLOGRAM_MINUTES);
		return 1;
	}

	/** One virtual core of each family (its first non-legacy spec) in a ring around the player. */
	private static int ring(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		List<FormationSpec> picks = new ArrayList<>();
		for (Family family : Family.values()) {
			for (FormationSpec spec : FormationRegistry.ofFamily(family)) {
				if (!spec.has(Flag.LEGACY_MIRROR)) {
					picks.add(spec);
					break;
				}
			}
		}
		for (int i = 0; i < picks.size(); i++) {
			double angle = Math.PI * 2.0D * i / picks.size();
			BlockPos eye = BlockPos.containing(player.getX() + Math.cos(angle) * RING_RADIUS, player.getY(),
					player.getZ() + Math.sin(angle) * RING_RADIUS);
			CoreRecord record = VirtualCores.place(player, level, picks.get(i), eye, VirtualCores.DEFAULT_MINUTES, true);
			Fx.send(level, Vec3.atCenterOf(eye), FxType.FORMASI, FormasiFxIds.AWAKEN, picks.get(i).netId(), (float) record.radius);
		}
		say(player, "kultivator.formasi.uji.ring", ChatFormatting.GREEN, picks.size(), VirtualCores.DEFAULT_MINUTES);
		return picks.size();
	}

	private static int remove(CommandContext<CommandSourceStack> ctx, boolean all, boolean real) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		CoreStore store = CoreTicker.store(level);
		if (!all) {
			CoreRecord record = VirtualCores.nearest(level, player.position());
			if (record == null) {
				say(player, "kultivator.formasi.uji.no_virtual", ChatFormatting.YELLOW);
				return 0;
			}
			store.remove(record.id);
			Stacking.invalidate();
			say(player, "kultivator.formasi.uji.removed", ChatFormatting.GRAY, name(record));
			return 1;
		}
		int removed = VirtualCores.clear(level);
		int forgotten = 0;
		if (real) {
			for (CoreStore each : CoreTicker.stores()) {
				ServerLevel eachLevel = each.dim() == null ? null : CoreTicker.level(each.dim());
				if (eachLevel == null) {
					continue;
				}
				for (CoreRecord record : each.list()) {
					if (!record.virtual && record.owner.equals(player.getUUID())) {
						CoreTicker.forget(eachLevel, each, record, false);
						forgotten++;
					}
				}
			}
		}
		Stacking.invalidate();
		say(player, "kultivator.formasi.uji.removed_all", ChatFormatting.GRAY, removed, forgotten);
		return removed + forgotten;
	}

	// ================================================================== listings

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int hallLevel = Math.max(1, CoreTicker.hallLevel(player.getUUID()));
		Language language = Language.getInstance();
		say(player, "kultivator.formasi.uji.list_head", ChatFormatting.GOLD, FormationRegistry.all().size(), hallLevel);
		for (FormationSpec spec : FormationRegistry.all()) {
			StringBuilder flags = new StringBuilder();
			for (Flag flag : spec.flags()) {
				flags.append(flags.isEmpty() ? "" : ",").append(flag.name().toLowerCase(Locale.ROOT));
			}
			MutableComponent line = Component.translatable("kultivator.formasi.uji.list_row", spec.id(), spec.netId(), spec.family().label(),
					spec.grade().label(), spec.minHallLevel(), spec.pattern().size(), spec.pattern().distinctRotations(),
					spec.scope().name().toLowerCase(Locale.ROOT), Formasi.fmt(spec.radiusAt(hallLevel)), flags.isEmpty() ? "-" : flags.toString())
					.withStyle(spec.family().moduleA() ? ChatFormatting.GRAY : ChatFormatting.DARK_AQUA);
			boolean lang = language.has(spec.key() + ".name") && language.has(spec.key() + ".desc") && language.has(spec.key() + ".hint");
			if (!lang) {
				line.append(Component.translatable("kultivator.formasi.uji.missing_lang").withStyle(ChatFormatting.RED));
			}
			if (!language.has(spec.key() + ".source")) {
				line.append(Component.translatable("kultivator.formasi.uji.missing_source").withStyle(ChatFormatting.RED));
			}
			Msg.chat(player, line);
		}
		return 1;
	}

	/** Registry audit, emit round trip, identical slot sets, the 6b fit table and a budget estimate. */
	private static int audit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<FormationSpec> specs = FormationRegistry.all();
		int noEffect = 0;
		int emitBad = 0;
		List<String> emitWho = new ArrayList<>();
		Map<String, String> signatures = new HashMap<>();
		List<String> twins = new ArrayList<>();
		for (FormationSpec spec : specs) {
			if (!spec.has(Flag.LEGACY_MIRROR) && FormationRegistry.effectOf(spec) == null) {
				noEffect++;
			}
			Pattern pattern = spec.pattern();
			for (int rot = 0; rot < pattern.distinctRotations(); rot++) {
				List<Slot> slots = pattern.slots(rot);
				List<PatternBlock> blocks = pattern.emit(rot);
				boolean bad = blocks.size() != slots.size() + 1;
				for (int i = 1; i < blocks.size() && !bad; i++) {
					PatternBlock block = blocks.get(i);
					Slot slot = slots.get(i - 1);
					BlockState state = block.state();
					bad = block.dx() != slot.dx() || block.dy() != slot.dy() || block.dz() != slot.dz()
							|| !slot.rule().test(state, state.getFluidState(), rot);
				}
				if (bad) {
					emitBad++;
					emitWho.add(spec.id() + "@" + rot);
				}
			}
			if (spec.has(Flag.LEGACY_MIRROR) || pattern.size() == 0) {
				continue;
			}
			for (int rot = 0; rot < pattern.distinctRotations(); rot++) {
				String signature = signature(pattern.slots(rot));
				String other = signatures.get(signature);
				if (other != null && !other.equals(spec.id())) {
					twins.add(other + "=" + spec.id());
				}
				signatures.putIfAbsent(signature, spec.id());
			}
		}
		say(player, "kultivator.formasi.uji.audit_head", ChatFormatting.GOLD, specs.size(), FormationRegistry.frozen() ? "ok" : "!", noEffect);
		say(player, "kultivator.formasi.uji.audit_emit", emitBad == 0 ? ChatFormatting.GREEN : ChatFormatting.RED, emitBad,
				emitWho.isEmpty() ? "-" : String.join(", ", emitWho));
		say(player, "kultivator.formasi.uji.audit_twins", twins.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW, twins.size(),
				twins.isEmpty() ? "-" : String.join(", ", twins));
		for (int radius : SectFormationSlots.ANCHOR_RADII) {
			List<String> ids = new ArrayList<>();
			for (FormationSpec spec : SectFormationSlots.fitting(radius)) {
				ids.add(spec.id());
			}
			say(player, "kultivator.formasi.uji.audit_fit", ChatFormatting.GRAY, radius, ids.size(), ids.isEmpty() ? "-" : String.join(", ", ids));
		}
		List<FormasiAnchor> anchors = SectBuildBridge.anchors(player.getUUID());
		say(player, "kultivator.formasi.uji.audit_anchors", ChatFormatting.GRAY, anchors.size());
		for (FormasiAnchor anchor : anchors) {
			say(player, "kultivator.formasi.uji.audit_anchor", ChatFormatting.DARK_GRAY, anchor.id(), anchor.radius(), anchor.tier(),
					SectFormationSlots.fitting(anchor.radius()).size());
		}
		int slots = 0;
		int cores = 0;
		for (CoreStore store : CoreTicker.stores()) {
			for (CoreRecord record : store.all()) {
				FormationSpec spec = FormationRegistry.byId(record.spec);
				if (!record.virtual && spec != null) {
					slots += spec.pattern().size() + 1;
					cores++;
				}
			}
		}
		int reads = KultivatorConfig.get().formationReadsPerTick;
		say(player, "kultivator.formasi.uji.audit_budget", ChatFormatting.AQUA, cores, slots, reads, Formasi.fmt(slots / (double) reads / 20.0D),
				Formasi.fmt(KultivatorConfig.get().formationBudgetMs));
		return 1;
	}

	private static String signature(List<Slot> slots) {
		TreeSet<String> parts = new TreeSet<>();
		for (Slot slot : slots) {
			parts.add(slot.dx() + "," + slot.dy() + "," + slot.dz() + ":" + slot.rule().label().getString());
		}
		return String.join(";", parts);
	}

	/** The looked-at core's fresh full match with every slot state, and its last verify time. */
	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		CoreRecord record = nearestCore(player);
		if (record == null) {
			return 0;
		}
		Formasi.describe(player, level, record);
		FormationSpec spec = FormationRegistry.byId(record.spec);
		if (spec == null) {
			return 0;
		}
		if (record.virtual) {
			say(player, "kultivator.formasi.uji.status_virtual", ChatFormatting.GRAY, Math.max(0L, (record.expires - level.getGameTime()) / 20L));
			return 1;
		}
		MatchResult result = PatternMatcher.full(new ChunkReader(level), record.eye, spec, record.rot);
		long ago = record.lastFullVerify <= 0L ? -1L : Math.max(0L, (level.getGameTime() - record.lastFullVerify) / 20L);
		say(player, "kultivator.formasi.uji.status_head", ChatFormatting.AQUA, result.ok(), result.total(), result.missing(), result.wrong(),
				result.unknown(), ago < 0L ? "-" : Long.toString(ago));
		StringBuilder line = new StringBuilder();
		byte[] states = result.states();
		for (int i = 0; i < states.length; i++) {
			line.append(switch (states[i]) {
				case ChunkReader.OK -> '+';
				case ChunkReader.MISSING -> 'o';
				case ChunkReader.WRONG -> 'x';
				default -> '?';
			});
			if (line.length() >= STATUS_LINE || i == states.length - 1) {
				Msg.chat(player, Component.literal(line.toString()).withStyle(ChatFormatting.DARK_GRAY));
				line.setLength(0);
			}
		}
		List<Slot> slots = spec.pattern().slots(record.rot);
		int shown = 0;
		for (int i = 0; i < states.length && i < slots.size() && shown < 12; i++) {
			if (states[i] == ChunkReader.MISSING || states[i] == ChunkReader.WRONG) {
				Slot slot = slots.get(i);
				BlockPos at = record.eye.offset(slot.dx(), slot.dy(), slot.dz());
				say(player, "kultivator.formasi.uji.status_slot", states[i] == ChunkReader.WRONG ? ChatFormatting.RED : ChatFormatting.GOLD,
						at.getX(), at.getY(), at.getZ(), slot.rule().label());
				shown++;
			}
		}
		say(player, "kultivator.formasi.uji.status_legend", ChatFormatting.DARK_GRAY);
		return 1;
	}

	/** The field at the player, each contributing core, and every cap of 24.5. */
	private static int stack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		Stacking.invalidate();
		FormationField field = Stacking.fieldAt(level, player.position(), player);
		say(player, "kultivator.formasi.uji.field_head", ChatFormatting.GOLD);
		say(player, "kultivator.formasi.uji.field_gather", ChatFormatting.AQUA, Formasi.fmt(field.gather()), Formasi.fmt(field.unite()),
				Formasi.fmt(field.stackCap()), Formasi.fmt(field.drain()));
		say(player, "kultivator.formasi.uji.field_heal", ChatFormatting.AQUA, field.healAmp(), Formasi.fmt(field.qiRegen()),
				Formasi.fmt(field.deviation()), Formasi.fmt(field.tribFactor()));
		say(player, "kultivator.formasi.uji.field_refine", ChatFormatting.AQUA, Formasi.fmt(field.refineChance()), field.refineSteps(),
				Formasi.fmt(field.refineTime()), Formasi.fmt(field.inscribe()), Formasi.fmt(field.quality()));
		say(player, "kultivator.formasi.uji.field_other", ChatFormatting.AQUA, field.layers(), field.concealGrade(), field.illusionGrade());
		List<Stacking.Contribution> parts = Stacking.contributions(level, player.position(), player);
		if (parts.isEmpty()) {
			say(player, "kultivator.formasi.uji.field_none", ChatFormatting.GRAY);
		}
		for (Stacking.Contribution part : parts) {
			FormationField own = part.field();
			say(player, "kultivator.formasi.uji.field_part", ChatFormatting.GRAY, part.spec().name(), part.record().id, Formasi.fmt(part.record().power),
					Formasi.fmt(own.gather()), Formasi.fmt(own.unite()), Formasi.fmt(own.drain()), Formasi.fmt(own.tribFactor()),
					Formasi.fmt(own.refineChance()), own.layers());
		}
		say(player, "kultivator.formasi.uji.field_caps", ChatFormatting.DARK_GRAY, Formasi.fmt(Stacking.GATHER_SECT_CAP),
				Formasi.fmt(Stacking.FORMATION_STACK_CAP), Formasi.fmt(Stacking.FORMATION_STACK_CAP_A1), Formasi.fmt(Stacking.DRAIN_FLOOR),
				Stacking.HEAL_AMP_CAP, Formasi.fmt(Stacking.QI_REGEN_CAP), Formasi.fmt(Stacking.DEVIATION_CAP));
		say(player, "kultivator.formasi.uji.field_caps2", ChatFormatting.DARK_GRAY, Formasi.fmt(Stacking.REFINE_CHANCE_CAP), Stacking.REFINE_STEP_CAP,
				Formasi.fmt(Stacking.REFINE_TIME_FLOOR), Formasi.fmt(Stacking.INSCRIBE_CAP), Formasi.fmt(Stacking.TRIB_FORMATION_FLOOR),
				Formasi.fmt(Stacking.QUALITY_FORMATION_CAP), Stacking.BARRIER_LAYER_CAP, Formasi.fmt(Stacking.P_CAP));
		return 1;
	}

	// ================================================================== numbers

	private static int daya(CommandContext<CommandSourceStack> ctx, @Nullable String target) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String text = StringArgumentType.getString(ctx, "jam");
		double hours;
		boolean full = "penuh".equalsIgnoreCase(text) || "full".equalsIgnoreCase(text);
		if (full) {
			hours = Double.MAX_VALUE;
		} else {
			try {
				hours = Math.max(0.0D, Double.parseDouble(text));
			} catch (NumberFormatException exception) {
				say(player, "kultivator.formasi.uji.bad_number", ChatFormatting.RED, text);
				return 0;
			}
		}
		List<CoreRecord> records = new ArrayList<>();
		if (target == null) {
			CoreRecord record = nearestCore(player);
			if (record != null) {
				records.add(record);
			}
		} else if ("semua".equalsIgnoreCase(target) || "all".equalsIgnoreCase(target)) {
			for (CoreStore store : CoreTicker.stores()) {
				for (CoreRecord record : store.all()) {
					if (record.owner.equals(player.getUUID())) {
						records.add(record);
					}
				}
			}
		} else {
			try {
				int id = Integer.parseInt(target);
				CoreRecord record = CoreTicker.store((ServerLevel) player.level()).byId(id);
				if (record != null) {
					records.add(record);
				}
			} catch (NumberFormatException exception) {
				say(player, "kultivator.formasi.uji.bad_number", ChatFormatting.RED, target);
				return 0;
			}
		}
		if (records.isEmpty()) {
			say(player, "kultivator.formasi.uji.no_core", ChatFormatting.YELLOW);
			return 0;
		}
		for (CoreRecord record : records) {
			FormationSpec spec = FormationRegistry.byId(record.spec);
			ServerLevel level = CoreTicker.level(record.dim);
			if (spec == null || level == null) {
				continue;
			}
			long cap = Upkeep.capCenti(spec);
			record.daya = full ? cap : Math.min(cap, Upkeep.hoursCenti(spec, hours));
			record.carry = 0.0D;
			CoreTicker.markDirty(level);
			CoreTicker.refreshState(level, record);
			say(player, "kultivator.formasi.uji.daya", ChatFormatting.GREEN, spec.name(), record.daya / 100L, cap / 100L, record.state.label());
		}
		return records.size();
	}

	private static int kas(CommandContext<CommandSourceStack> ctx, @Nullable Boolean stone) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		long n = IntegerArgumentType.getInteger(ctx, "n");
		long amethyst = Kas.amethyst(owner) / 100L;
		long stones = Kas.stone(owner) / 100L;
		int best = Math.max(0, Kas.bestGrade(owner));
		if (stone == null || !stone) {
			amethyst = n;
		}
		if (stone == null || stone) {
			stones = n;
		}
		Kas.set(owner, amethyst, stones, best);
		say(player, "kultivator.formasi.uji.kas", ChatFormatting.GREEN, Kas.amethyst(owner) / 100L, Kas.stone(owner) / 100L);
		return 1;
	}

	private static int hall(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int level = IntegerArgumentType.getInteger(ctx, "tingkat");
		CoreTicker.pretendHall(player.getUUID(), level);
		Stacking.invalidate();
		say(player, level <= 0 ? "kultivator.formasi.uji.hall_reset" : "kultivator.formasi.uji.hall", ChatFormatting.GREEN, level);
		return 1;
	}

	private static int keeper(CommandContext<CommandSourceStack> ctx, KeeperOrder order) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CoreRecord record = nearestCore(player);
		if (record == null) {
			return 0;
		}
		DiscipleEntity nearest = null;
		double bestDistance = Double.MAX_VALUE;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			double distance = disciple.distanceToSqr(player);
			if (disciple.level() == player.level() && distance < bestDistance) {
				nearest = disciple;
				bestDistance = distance;
			}
		}
		if (nearest == null) {
			say(player, "kultivator.formasi.uji.no_disciple", ChatFormatting.YELLOW);
			return 0;
		}
		Component why = Keepers.assign(player, (ServerLevel) player.level(), record, nearest, order, true);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		say(player, "kultivator.formasi.keeper.assigned", ChatFormatting.GREEN, nearest.getName());
		return 1;
	}

	private static int pretend(CommandContext<CommandSourceStack> ctx, int rank, @Nullable String daoId) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String realmId = StringArgumentType.getString(ctx, "ranah");
		Realm realm = Realm.byId(realmId);
		if (realm == null) {
			say(player, "kultivator.formasi.uji.bad_realm", ChatFormatting.RED, realmId);
			return 0;
		}
		Dao dao = daoId == null ? null : Dao.byId(daoId);
		if (daoId != null && dao == null) {
			say(player, "kultivator.formasi.uji.bad_dao", ChatFormatting.RED, daoId);
			return 0;
		}
		CoreRecord record = nearestCore(player);
		if (record == null) {
			return 0;
		}
		record.pretend.add(new CoreRecord.Pretend(realm, rank, dao, KeeperOrder.JAGA));
		Stacking.invalidate();
		say(player, "kultivator.formasi.uji.pretend", ChatFormatting.GREEN, name(record), realm.displayName(), rank,
				dao == null ? "-" : dao.id());
		return 1;
	}

	/** The strike factor a tribulation would use here now (formations, the Pelindung and the pill together). */
	private static int tribulation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Rank target = Tribulation.target(player);
		if (target == null) {
			target = new Rank(Realm.GOLDEN_CORE, 0, 0);
		}
		Stacking.invalidate();
		FormationField field = Stacking.fieldAt((ServerLevel) player.level(), player.position(), player);
		double total = Tribulation.strikeFactor(player, target, 1, false);
		say(player, "kultivator.formasi.uji.tribulation", ChatFormatting.AQUA, Formasi.fmt(field.tribFactor()), Formasi.fmt(total),
				Formasi.fmt(Stacking.TRIB_FORMATION_FLOOR), Formasi.fmt(Tribulation.STRIKE_FACTOR_FLOOR));
		return 1;
	}

	// ================================================================== performance and debug

	private static int performance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int loaded = 0;
		int sleeping = 0;
		int virtual = 0;
		for (CoreStore store : CoreTicker.stores()) {
			ServerLevel level = store.dim() == null ? null : CoreTicker.level(store.dim());
			for (CoreRecord record : store.all()) {
				if (record.virtual) {
					virtual++;
				} else if (level != null && level.isPositionEntityTicking(record.eye)) {
					loaded++;
				} else {
					sleeping++;
				}
			}
		}
		int[] cache = Stacking.takeStats();
		say(player, "kultivator.formasi.uji.perf", ChatFormatting.AQUA, String.format(Locale.ROOT, "%.3f", CoreTicker.averageMs()),
				Formasi.fmt(KultivatorConfig.get().formationBudgetMs), Formasi.fmt(Verifier.readsPerSecond() / 20.0D),
				KultivatorConfig.get().formationReadsPerTick, CoreTicker.pulsesPerSecond(), IntruderSnapshot.takeQueries());
		say(player, "kultivator.formasi.uji.perf2", ChatFormatting.GRAY, CoreTicker.throttle(), CoreTicker.slow() ? "!" : "-", loaded, sleeping,
				virtual, cache[0], cache[1]);
		return 1;
	}

	private static int verify(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		CoreRecord record = nearestCore(player);
		FormationSpec spec = record == null ? null : FormationRegistry.byId(record.spec);
		if (record == null || spec == null || record.virtual) {
			return 0;
		}
		MatchResult result = PatternMatcher.full(new ChunkReader(level), record.eye, spec, record.rot);
		Verifier.apply(record, result);
		record.lastFullVerify = level.getGameTime();
		CoreTicker.refreshState(level, record);
		say(player, "kultivator.formasi.uji.verified", ChatFormatting.GREEN, spec.name(), result.ok(), result.total(), record.state.label());
		return 1;
	}

	private static int clearDimension(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		CoreStore store = CoreTicker.store(level);
		int count = 0;
		for (CoreRecord record : store.list()) {
			if (record.virtual) {
				store.remove(record.id);
			} else {
				CoreTicker.forget(level, store, record, false);
			}
			count++;
		}
		Stacking.invalidate();
		say(player, "kultivator.formasi.uji.cleared", ChatFormatting.YELLOW, count);
		return count;
	}

	private FormasiTests() {
	}
}

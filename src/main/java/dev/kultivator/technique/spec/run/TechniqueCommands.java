package dev.kultivator.technique.spec.run;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Feat;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * {@code /kultivasi teknik ...} and {@code debug teknik ...} (DESIGN 22.16.1, 22.16.3), called once per root from
 * {@code CultivateCommand}, plus the small bridges {@code Techniques} calls into this package.
 */
public final class TechniqueCommands {

	private static final String K = "kultivator.teknik.cmd.";
	private static final int GOLD = 0xF2C14E;
	private static final int MUTED = 0xA8B5B0;
	private static final int JADE = 0x7CFFB2;
	private static final int REFUSE = 0xFF8A7A;
	private static final String ALL = "semua";

	private static final SuggestionProvider<CommandSourceStack> SPECS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(TechniqueRegistry.all().stream().filter(TechniqueSpec::playerVisible).map(TechniqueSpec::id), builder);
	private static final SuggestionProvider<CommandSourceStack> SPECS_ALL = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.concat(Stream.of(ALL), TechniqueRegistry.all().stream().map(TechniqueSpec::id)), builder);
	private static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		Stream<String> ids = player == null ? Stream.empty() : Cultivators.get(player).learnedSpecs().stream().map(TechniqueSpec::id);
		return SharedSuggestionProvider.suggest(ids, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> KNOWN_OR_EMPTY = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		Stream<String> ids = player == null ? Stream.empty() : Cultivators.get(player).learnedSpecs().stream().map(TechniqueSpec::id);
		return SharedSuggestionProvider.suggest(Stream.concat(Stream.of("kosong"), ids), builder);
	};
	private static final SuggestionProvider<CommandSourceStack> KNOWN_ALL = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		Stream<String> ids = player == null ? Stream.empty() : Cultivators.get(player).learnedSpecs().stream().map(TechniqueSpec::id);
		return SharedSuggestionProvider.suggest(Stream.concat(Stream.of(ALL), ids), builder);
	};
	private static final SuggestionProvider<CommandSourceStack> DAOS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.concat(Arrays.stream(Dao.values()).map(Dao::id), Stream.of("universal")), builder);
	private static final SuggestionProvider<CommandSourceStack> GRADES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(TechniqueGrade.values()).map(TechniqueGrade::id), builder);
	private static final SuggestionProvider<CommandSourceStack> TYPES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(TechniqueType.values()).map(TechniqueType::id), builder);
	private static final SuggestionProvider<CommandSourceStack> FEATS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Feat.Kind.values()).map(kind -> kind.name().toLowerCase(Locale.ROOT)), builder);
	private static final SuggestionProvider<CommandSourceStack> DISCIPLES = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		Stream<String> names = player == null ? Stream.empty()
				: Disciples.loaded(player.getUUID()).stream().map(entity -> entity.getName().getString().replace(' ', '_'));
		return SharedSuggestionProvider.suggest(Stream.concat(Stream.of(ALL), names), builder);
	};

	public static void add(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("teknik")
				.executes(ctx -> openCodex(self(ctx), null))
				.then(Commands.literal("kitab")
						.executes(ctx -> openCodex(self(ctx), null))
						.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
								.executes(ctx -> openCodex(self(ctx), StringArgumentType.getString(ctx, "id")))))
				.then(Commands.literal("info")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
								.executes(ctx -> info(self(ctx), StringArgumentType.getString(ctx, "id")))))
				.then(Commands.literal("pilih")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN)
								.executes(ctx -> Techniques.select(self(ctx), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
				.then(Commands.literal("rapal")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN)
								.executes(ctx -> Techniques.tryCast(self(ctx), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
				.then(Commands.literal("daftar")
						.executes(ctx -> list(self(ctx), spec -> true, Component.translatable(K + "list.all")))
						.then(Commands.literal("dao")
								.then(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS)
										.executes(ctx -> listDao(self(ctx), StringArgumentType.getString(ctx, "dao")))))
						.then(Commands.literal("tingkat")
								.then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
										.executes(ctx -> listGrade(self(ctx), StringArgumentType.getString(ctx, "grade")))))
						.then(Commands.literal("jenis")
								.then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
										.executes(ctx -> listType(self(ctx), StringArgumentType.getString(ctx, "type"))))))
				.then(Commands.literal("slot")
						.executes(ctx -> showSlots(self(ctx)))
						.then(Commands.literal("isi").executes(ctx -> autofill(self(ctx))))
						.then(Commands.literal("hapus").executes(ctx -> clearSlots(self(ctx))))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 12))
								.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN_OR_EMPTY)
										.executes(ctx -> slot(self(ctx), IntegerArgumentType.getInteger(ctx, "n"),
												StringArgumentType.getString(ctx, "id"))))))
				// the click of a 9-star title's Dewa offer: the master picks the disciple's art (22.13.2)
				.then(Commands.literal("pahamkan")
						.then(Commands.argument("disciple", StringArgumentType.word())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
										.executes(ctx -> Codex.dewaChoiceCommand(self(ctx), StringArgumentType.getString(ctx, "disciple"),
												StringArgumentType.getString(ctx, "id")))))));

		debug.then(Commands.literal("teknik")
				.then(Commands.literal("mastery")
						.then(Commands.literal("tambah")
								.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
										.then(Commands.argument("points", IntegerArgumentType.integer(-100000, 100000))
												.executes(ctx -> addMastery(self(ctx), StringArgumentType.getString(ctx, "id"),
														IntegerArgumentType.getInteger(ctx, "points"))))))
						.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS_ALL)
								.then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
										.executes(ctx -> setMastery(self(ctx), StringArgumentType.getString(ctx, "id"),
												IntegerArgumentType.getInteger(ctx, "level"))))))
				.then(Commands.literal("cooldowns")
						.then(Commands.literal("reset").executes(ctx -> {
							Techniques.resetCooldowns(self(ctx));
							Msg.chat(self(ctx), Component.translatable(K + "cooldowns_reset").withColor(JADE));
							return 1;
						})))
				.then(Commands.literal("lupakan")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(KNOWN_ALL)
								.executes(ctx -> forget(self(ctx), StringArgumentType.getString(ctx, "id")))))
				.then(Commands.literal("kekuatan").executes(ctx -> power(self(ctx))))
				.then(Commands.literal("beban").executes(ctx -> load(self(ctx))))
				.then(Commands.literal("feat")
						.then(Commands.argument("kind", StringArgumentType.word()).suggests(FEATS)
								.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000000))
										.executes(ctx -> feat(self(ctx), StringArgumentType.getString(ctx, "kind"),
												IntegerArgumentType.getInteger(ctx, "n")))))));
		debug.then(Commands.literal("murid")
				.then(Commands.literal("teknik")
						.then(Commands.literal("mastery")
								.then(Commands.argument("name", StringArgumentType.word()).suggests(DISCIPLES)
										.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS_ALL)
												.then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
														.executes(ctx -> discipleMastery(self(ctx), StringArgumentType.getString(ctx, "name"),
																StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "level")))))))));
	}

	// ================================================================== bridges for Techniques

	/** An Enlightenment Moment began (called by {@code Techniques.tick} on the rising edge). */
	public static void enlightenment(ServerPlayer player) {
		Unlocks.onEnlightenment(player);
	}

	/** The technique advancements (every 100 ticks from {@code Techniques.tick}). */
	public static void checkAdvancements(ServerPlayer player) {
		Unlocks.checkAdvancements(player, Cultivators.get(player));
	}

	/** Ends the player's technique form (UBAH), if any. */
	public static void endForm(ServerPlayer player) {
		Forms.end(player, EndReason.CANCELLED);
	}

	/** Clears the global cooldown, the lockout and the charges (debug and test cooldown resets). */
	public static void resetSession(ServerPlayer player) {
		TechniqueRunner.resetSession(player);
	}

	// ================================================================== player commands

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static @Nullable TechniqueSpec spec(ServerPlayer player, String id) {
		TechniqueSpec spec = TechniqueRegistry.byId(id.toLowerCase(Locale.ROOT));
		if (spec == null) {
			Msg.chat(player, Component.translatable(K + "unknown", id).withColor(REFUSE));
		}
		return spec;
	}

	private static int openCodex(ServerPlayer player, @Nullable String id) {
		int net = -1;
		if (id != null) {
			TechniqueSpec spec = spec(player, id);
			if (spec == null) {
				return 0;
			}
			net = spec.netId();
		}
		Net.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.CODEX, net));
		return 1;
	}

	/** Name, Sumber, live numbers, mastery and the acquisition checklist. */
	private static int info(ServerPlayer player, String id) {
		TechniqueSpec spec = spec(player, id);
		if (spec == null) {
			return 0;
		}
		CultivationData data = Cultivators.get(player);
		int mastery = Mastery.level(data, spec.id());
		int points = Mastery.points(data, spec.id());
		double p = TechniquePower.power(data, spec);
		Msg.chat(player, spec.displayName().copy().withColor(spec.grade().ribbon()).withStyle(ChatFormatting.BOLD));
		player.sendSystemMessage(Component.translatable(K + "info.kind", spec.grade().displayName(), TechniqueGrade.subName(spec.sub()),
				spec.type().displayName(), spec.dao() == null ? Component.translatable("kultivator.teknik.theme.universal") : spec.dao().displayName(),
				spec.minRank().displayName()).withColor(MUTED));
		player.sendSystemMessage(spec.source().copy().withStyle(ChatFormatting.ITALIC).withColor(0xC9B98F));
		player.sendSystemMessage(spec.description().copy().withColor(0xE8E8E8));
		double coefficient = Loadout.coefficient(spec);
		player.sendSystemMessage(Component.translatable(K + "info.numbers", Msg.number(p), String.format(Locale.ROOT, "%.2f", coefficient),
				Msg.number(coefficient * p * TechniquePower.masteryDamage(mastery)), String.valueOf(TechniquePower.qi(spec, data, mastery)),
				TechniqueRunner.seconds(TechniquePower.cooldownTicks(spec, data, mastery))).withColor(0xE8E8E8));
		int next = mastery >= 4 ? 0 : Mastery.nextThreshold(spec.grade(), mastery);
		player.sendSystemMessage(Component.translatable(mastery >= 4 ? K + "info.mastery_max" : K + "info.mastery", Mastery.levelName(mastery),
				String.valueOf(points), String.valueOf(next)).withColor(GOLD));
		if (data.knows(spec.id())) {
			player.sendSystemMessage(Component.translatable(K + "info.known").withColor(JADE));
		} else {
			player.sendSystemMessage(Component.translatable(K + "info.how").withColor(MUTED));
		}
		for (Unlocks.Line line : Unlocks.checklist(data, spec)) {
			MutableComponent row = Component.literal(line.met() ? " ✔ " : " ✘ ").withColor(line.met() ? JADE : REFUSE).append(line.text().copy()
					.withColor(line.met() ? 0xD8F5E4 : 0xE8E8E8));
			if (line.goal() > 0 && !line.met()) {
				row.append(Component.literal(" (" + line.progress() + "/" + line.goal() + ")").withColor(MUTED));
			}
			player.sendSystemMessage(row);
		}
		return 1;
	}

	private static int listDao(ServerPlayer player, String id) {
		if ("universal".equals(id)) {
			return list(player, spec -> spec.dao() == null, Component.translatable("kultivator.teknik.theme.universal"));
		}
		Dao dao = Dao.byId(id);
		if (dao == null) {
			Msg.chat(player, Component.translatable(K + "unknown", id).withColor(REFUSE));
			return 0;
		}
		return list(player, spec -> spec.dao() == dao, dao.displayName());
	}

	private static int listGrade(ServerPlayer player, String id) {
		TechniqueGrade grade = TechniqueGrade.byId(id);
		if (grade == null) {
			Msg.chat(player, Component.translatable(K + "unknown", id).withColor(REFUSE));
			return 0;
		}
		return list(player, spec -> spec.grade() == grade, grade.displayName());
	}

	private static int listType(ServerPlayer player, String id) {
		TechniqueType type = TechniqueType.byId(id);
		if (type == null) {
			Msg.chat(player, Component.translatable(K + "unknown", id).withColor(REFUSE));
			return 0;
		}
		return list(player, spec -> spec.type() == type, type.displayName());
	}

	/** Known specs matching {@code filter}, with mastery and state. */
	private static int list(ServerPlayer player, Predicate<TechniqueSpec> filter, Component title) {
		CultivationData data = Cultivators.get(player);
		List<TechniqueSpec> known = new ArrayList<>();
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (spec.playerVisible() && filter.test(spec)) {
				known.add(spec);
			}
		}
		Msg.chat(player, Component.translatable(K + "list.header", title, String.valueOf(known.size()),
				String.valueOf(PlayerCaster.known(data)), String.valueOf(TechniqueRegistry.playerTotal())).withColor(GOLD));
		if (known.isEmpty()) {
			player.sendSystemMessage(Component.translatable(K + "list.empty").withColor(MUTED));
			return 0;
		}
		long now = player.level().getGameTime();
		String selected = data.selectedId();
		for (TechniqueSpec spec : known) {
			int mastery = Mastery.level(data, spec.id());
			MutableComponent line = Component.literal(" • ").withColor(MUTED)
					.append(spec.displayName().copy().withColor(spec.grade().ribbon()))
					.append(Component.literal(" " + spec.id()).withColor(0x6F7A76))
					.append(Component.literal(" · ").withColor(MUTED)).append(spec.grade().displayName().copy().withColor(MUTED))
					.append(Component.literal(" · ").withColor(MUTED)).append(Mastery.levelName(mastery).copy().withColor(GOLD));
			Component state;
			if (spec.type() == TechniqueType.PASIF) {
				state = Component.translatable(K + "state.passive").withColor(JADE);
			} else {
				Component why = TechniqueRunner.whyNotCastablePublic(data, spec);
				long left = data.cooldownLeft(spec.id(), now);
				if (why != null) {
					state = why.copy().withColor(REFUSE);
				} else if (left > 0L) {
					state = Component.translatable(K + "state.waiting", TechniqueRunner.seconds(left)).withColor(0xFFC27A);
				} else {
					state = Component.translatable(K + "state.ready").withColor(JADE);
				}
			}
			line.append(Component.literal("  [").withColor(MUTED)).append(state).append(Component.literal("]").withColor(MUTED));
			if (spec.id().equals(selected)) {
				line.append(Component.literal(" ").append(Component.translatable(K + "state.selected").withColor(GOLD)));
			}
			player.sendSystemMessage(line);
		}
		return known.size();
	}

	private static int showSlots(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		String[] slots = Loadout.slots(data);
		Msg.chat(player, Component.translatable(K + "slot.header").withColor(GOLD));
		for (int i = 0; i < slots.length; i++) {
			TechniqueSpec spec = TechniqueRegistry.byId(slots[i]);
			Component name = spec == null ? Component.translatable(K + "slot.empty").withColor(MUTED) : spec.displayName().copy().withColor(spec.grade().ribbon());
			player.sendSystemMessage(Component.literal(" " + (i + 1) + ". ").withColor(MUTED).append(name));
		}
		return 1;
	}

	private static int slot(ServerPlayer player, int n, String id) {
		if ("kosong".equals(id) || "-".equals(id)) {
			Loadout.set(player, n - 1, "");
			Msg.chat(player, Component.translatable(K + "slot.cleared", n).withColor(MUTED));
			return 1;
		}
		TechniqueSpec spec = spec(player, id);
		if (spec == null) {
			return 0;
		}
		if (!Loadout.set(player, n - 1, spec.id())) {
			Msg.chat(player, Component.translatable(K + "slot.refused", spec.displayName()).withColor(REFUSE));
			return 0;
		}
		Msg.chat(player, Component.translatable(K + "slot.set", n, spec.displayName()).withColor(JADE));
		return 1;
	}

	private static int autofill(ServerPlayer player) {
		int filled = Loadout.autofill(player);
		Msg.chat(player, Component.translatable(K + "slot.autofill", filled).withColor(JADE));
		return filled;
	}

	private static int clearSlots(ServerPlayer player) {
		Loadout.clear(player);
		Msg.chat(player, Component.translatable(K + "slot.clear").withColor(MUTED));
		return 1;
	}

	// ================================================================== debug commands

	private static int setMastery(ServerPlayer player, String id, int level) {
		if (ALL.equals(id)) {
			CultivationData data = Cultivators.get(player);
			int count = 0;
			for (TechniqueSpec spec : data.learnedSpecs()) {
				Mastery.setLevel(player, spec.id(), level);
				count++;
			}
			Msg.chat(player, Component.translatable(K + "mastery.all", count, Mastery.levelName(level)).withColor(JADE));
			return count;
		}
		TechniqueSpec spec = spec(player, id);
		if (spec == null) {
			return 0;
		}
		Mastery.setLevel(player, spec.id(), level);
		Msg.chat(player, Component.translatable(K + "mastery.set", spec.displayName(), Mastery.levelName(level)).withColor(JADE));
		return 1;
	}

	private static int addMastery(ServerPlayer player, String id, int points) {
		TechniqueSpec spec = spec(player, id);
		if (spec == null) {
			return 0;
		}
		Mastery.add(player, spec.id(), points, Mastery.Source.COMMAND);
		CultivationData data = Cultivators.get(player);
		Msg.chat(player, Component.translatable(K + "mastery.added", points, spec.displayName(), Mastery.points(data, spec.id()),
				Mastery.levelName(Mastery.level(data, spec.id()))).withColor(JADE));
		return 1;
	}

	private static int forget(ServerPlayer player, String id) {
		CultivationData data = Cultivators.get(player);
		int count = 0;
		if (ALL.equals(id)) {
			for (String known : List.copyOf(data.learnedIds())) {
				data.forget(known);
				count++;
			}
		} else {
			TechniqueSpec spec = spec(player, id);
			if (spec == null) {
				return 0;
			}
			if (data.knows(spec.id())) {
				data.forget(spec.id());
				count = 1;
			}
		}
		TechniqueRunner.endAll(player, EndReason.CANCELLED);
		Cultivators.syncNow(player);
		Msg.chat(player, Component.translatable(K + "forgot", count).withColor(MUTED));
		return count;
	}

	/** R, C, Q, S, P, A/D/N, t, tierGap of the selected spec, boss cap per grade, K and the block cap. */
	private static int power(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		int stars = TechniquePower.playerStars(data);
		Msg.chat(player, Component.translatable(K + "power.title").withColor(GOLD));
		player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "R %.3f · C %.2f · Q %.2f · S %.3f (%d★) · P %s · D %s",
				TechniquePower.rankFactor(data.rank()), TechniquePower.comprehensionFactor(data, null), Math.sqrt(Math.max(0.01D, data.qualityFactor())),
				TechniquePower.starFactor(stars), stars, Msg.number(TechniquePower.power(data)), Msg.number(TechniquePower.legacyD(data)))).withColor(0xE8E8E8));
		// audit M4 T5/S7: which foes use D instead of P, and the Immortal Ascension layer term inside D
		player.sendSystemMessage(Component.translatable("kultivator.kekuatan.power.trial", Msg.number(TechniquePower.legacyD(data) * TechniquePower.TRIAL_MOB_FACTOR),
				Msg.number(TechniquePower.legacyLayer(data.rank()))).withColor(0xB0B0B0));
		TechniqueSpec selected = TechniqueRegistry.byId(data.selectedId());
		if (selected != null) {
			TechniquePower.Factors f = TechniquePower.factors(data, selected);
			int mastery = Mastery.level(data, selected.id());
			player.sendSystemMessage(Component.translatable(K + "power.spec", selected.displayName(), String.format(Locale.ROOT,
					"P %s · A %.2f · D %.2f · N %.2f · t %d · tierGap %d · gapR %d · above %d · M %d · qi %d · cd %ss",
					Msg.number(TechniquePower.power(data, selected)), f.a(), f.d(), f.n(), f.t(), f.tierGap(), f.gapR(), f.above(), mastery,
					TechniquePower.qi(selected, data, mastery), TechniqueRunner.seconds(TechniquePower.cooldownTicks(selected, data, mastery)))).withColor(0xE8E8E8));
		}
		StringBuilder caps = new StringBuilder();
		for (TechniqueGrade grade : TechniqueGrade.values()) {
			if (!caps.isEmpty()) {
				caps.append(" · ");
			}
			caps.append(grade.id()).append(' ').append(String.format(Locale.ROOT, "%.0f%%", TechniquePower.bossShare(data, grade) * 100.0D));
		}
		player.sendSystemMessage(Component.translatable(K + "power.boss", caps.toString()).withColor(0xE8E8E8));
		player.sendSystemMessage(Component.translatable(K + "power.kehancuran", String.format(Locale.ROOT, "%.2f", TechniquePower.kScale(data)),
				TechniquePower.blockCap(data), Kehancuran.selfOn(player) ? Component.translatable(K + "on") : Component.translatable(K + "off")).withColor(0xE8E8E8));
		return 1;
	}

	/** Active casts, phantoms, projectiles, DOT/BIND counts, FX events in the last second and Kehancuran jobs. */
	private static int load(ServerPlayer player) {
		int[] casts = TechniqueRunner.counts();
		int[] status = StatusTracker.counts();
		int[] boons = Boons.counts();
		int[] jobs = Kehancuran.jobCounts();
		Msg.chat(player, Component.translatable(K + "load.title").withColor(GOLD));
		player.sendSystemMessage(Component.translatable(K + "load.casts", casts[0], casts[1], KultivatorConfigView.maxActive(),
				TechniqueRunner.lowLoad() ? Component.translatable(K + "load.low") : Component.literal("")).withColor(0xE8E8E8));
		player.sendSystemMessage(Component.translatable(K + "load.entities", Phantoms.count(), VirtualProjectiles.count(), Fields.count(),
				Dummies.count()).withColor(0xE8E8E8));
		player.sendSystemMessage(Component.translatable(K + "load.status", status[0], status[1], status[2], status[3], boons[0], boons[1], boons[2])
				.withColor(0xE8E8E8));
		player.sendSystemMessage(Component.translatable(K + "load.fx", Caster.fxLastSecond()).withColor(0xE8E8E8));
		player.sendSystemMessage(Component.translatable(K + "load.jobs", jobs[0], jobs[1], jobs[2]).withColor(0xE8E8E8));
		return 1;
	}

	private static int feat(ServerPlayer player, String kindId, int n) {
		Feat.Kind kind;
		try {
			kind = Feat.Kind.valueOf(kindId.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			Msg.chat(player, Component.translatable(K + "unknown", kindId).withColor(REFUSE));
			return 0;
		}
		int changed = FeatTracker.set(player, kind, n);
		Unlocks.refresh(player);
		Msg.chat(player, Component.translatable(K + "feat", kindId, n, changed).withColor(JADE));
		return 1;
	}

	private static int discipleMastery(ServerPlayer player, String name, String id, int level) {
		List<DiscipleEntity> found = disciples(player, name);
		if (found.isEmpty()) {
			Msg.chat(player, Component.translatable(K + "no_disciple", name).withColor(REFUSE));
			return 0;
		}
		TechniqueSpec only = null;
		if (!ALL.equals(id)) {
			only = spec(player, id);
			if (only == null) {
				return 0;
			}
		}
		for (DiscipleEntity entity : found) {
			DiscipleData data = entity.data();
			int count = 0;
			if (only != null) {
				Mastery.setDiscipleLevel(data, only.id(), level);
				count = 1;
			} else {
				for (String known : data.learnedIds()) {
					if (TechniqueRegistry.byId(known) != null) {
						Mastery.setDiscipleLevel(data, known, level);
						count++;
					}
				}
			}
			entity.refreshSynced();
			Msg.chat(player, Component.translatable(K + "disciple_mastery", entity.getName(), count, Mastery.levelName(level)).withColor(JADE));
		}
		return found.size();
	}

	/** The player's loaded disciples named {@code name} (underscores for spaces, any case), or all of them for "semua". */
	static List<DiscipleEntity> disciples(ServerPlayer player, String name) {
		List<DiscipleEntity> found = new ArrayList<>();
		for (DiscipleEntity entity : Disciples.loaded(player.getUUID())) {
			if (ALL.equals(name) || entity.getName().getString().replace(' ', '_').equalsIgnoreCase(name)) {
				found.add(entity);
			}
		}
		return found;
	}

	/** The server's cast limit, read here so the command output does not import the config twice. */
	private static final class KultivatorConfigView {
		static int maxActive() {
			return dev.kultivator.KultivatorConfig.get().techniqueMaxActiveCasts;
		}
	}

	private TechniqueCommands() {
	}
}

package dev.kultivator.technique.spec.run;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * {@code uji teknik ...} and {@code uji boneka ...} (DESIGN 22.16.2), added to the op-only {@code uji} node by
 * {@code TestCommands}. A {@code teknik} literal added here merges with the existing one (brigadier merges same-named
 * literal nodes; the later {@code executes} wins).
 */
public final class TechniqueTests {

	private static final String K = "kultivator.teknik.test.";
	private static final int GOLD = 0xF2C14E;
	private static final int MUTED = 0xA8B5B0;
	private static final int JADE = 0x7CFFB2;
	private static final int REFUSE = 0xFF8A7A;
	/** Showcase: default, minimum and maximum seconds per technique. */
	private static final int SHOWCASE_DEFAULT = 4;
	private static final int SHOWCASE_MIN = 2;
	private static final int SHOWCASE_MAX_TICKS = 300;
	/** A tally prints when its cast ended or after this many ticks. */
	private static final int TALLY_TICKS = 400;

	/** Players whose {@code uji} and showcase casts may carve ({@code uji teknik kehancuran nyala}). */
	private static final Set<UUID> CARVE = new HashSet<>();
	private static final Map<UUID, Showcase> SHOWCASES = new HashMap<>();
	/** {@code uji teknik <id>} waiting for its cast to be created, and the tallies being collected. */
	private static final Map<UUID, Tally> PENDING = new HashMap<>();
	private static final List<Tally> TALLIES = new ArrayList<>();

	private static final SuggestionProvider<CommandSourceStack> SPECS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(TechniqueRegistry.all().stream().map(TechniqueSpec::id), builder);
	private static final SuggestionProvider<CommandSourceStack> SPECS_ALL = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.concat(Stream.of("semua"), TechniqueRegistry.all().stream().map(TechniqueSpec::id)), builder);
	private static final SuggestionProvider<CommandSourceStack> DAOS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.concat(java.util.Arrays.stream(Dao.values()).map(Dao::id), Stream.of("universal")), builder);
	private static final SuggestionProvider<CommandSourceStack> GRADES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.Arrays.stream(TechniqueGrade.values()).map(TechniqueGrade::id), builder);
	private static final SuggestionProvider<CommandSourceStack> DISCIPLES = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		Stream<String> names = player == null ? Stream.empty() : dev.kultivator.disciple.Disciples.loaded(player.getUUID()).stream()
				.map(entity -> entity.getName().getString().replace(' ', '_'));
		return SharedSuggestionProvider.suggest(Stream.concat(Stream.of("semua"), names), builder);
	};

	/** One running showcase. */
	private static final class Showcase {
		final List<TechniqueSpec> list;
		final int seconds;
		final ResourceKey<Level> dimension;
		int index;
		long next;

		Showcase(List<TechniqueSpec> list, int seconds, ResourceKey<Level> dimension, long next) {
			this.list = list;
			this.seconds = seconds;
			this.dimension = dimension;
			this.next = next;
		}
	}

	/** The per-target damage of one {@code uji teknik <id>} cast. */
	static final class Tally {
		final UUID owner;
		final String specId;
		final long created;
		@Nullable Caster cast;
		final Map<UUID, double[]> byTarget = new LinkedHashMap<>();
		final Map<UUID, Component> names = new HashMap<>();
		final Set<UUID> bosses = new HashSet<>();

		Tally(UUID owner, String specId, long created) {
			this.owner = owner;
			this.specId = specId;
			this.created = created;
		}

		void add(LivingEntity victim, double raw, double dealt) {
			double[] entry = byTarget.computeIfAbsent(victim.getUUID(), id -> new double[3]);
			entry[0] += raw;
			entry[1] += dealt;
			entry[2]++;
			names.putIfAbsent(victim.getUUID(), victim.getDisplayName());
			if (TechniquePower.isBoss(victim)) {
				bosses.add(victim.getUUID());
			}
		}
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TechniqueTests::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			SHOWCASES.remove(player.getUUID());
			PENDING.remove(player.getUUID());
			CARVE.remove(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHOWCASES.clear();
			PENDING.clear();
			TALLIES.clear();
			CARVE.clear();
		});
	}

	/** True while {@code uji teknik kehancuran nyala} lets this player's test and showcase casts carve (22.11.2 rule 7). */
	public static boolean kehancuranTests(ServerPlayer player) {
		return CARVE.contains(player.getUUID());
	}

	/** The pending tally of {@code owner} for {@code specId}, bound to the cast being created (from the Caster constructor). */
	static @Nullable Tally claim(UUID owner, String specId, Caster cast) {
		Tally tally = PENDING.get(owner);
		if (tally == null || !tally.specId.equals(specId)) {
			return null;
		}
		PENDING.remove(owner);
		tally.cast = cast;
		TALLIES.add(tally);
		return tally;
	}

	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("teknik")
				.executes(ctx -> learnAll(self(ctx), false))
				.then(Commands.literal("semua").executes(ctx -> learnAll(self(ctx), true)))
				.then(Commands.literal("pamer")
						.executes(ctx -> showcase(self(ctx), SHOWCASE_DEFAULT, null, null, null))
						.then(Commands.literal("stop").executes(ctx -> stopShowcase(self(ctx), true)))
						.then(Commands.literal("dao")
								.then(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS)
										.executes(ctx -> showcase(self(ctx), SHOWCASE_DEFAULT, StringArgumentType.getString(ctx, "dao"), null, null))))
						.then(Commands.literal("tingkat")
								.then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
										.executes(ctx -> showcase(self(ctx), SHOWCASE_DEFAULT, null, StringArgumentType.getString(ctx, "grade"), null))))
						.then(Commands.literal("dari")
								.then(Commands.argument("from", StringArgumentType.word()).suggests(SPECS)
										.executes(ctx -> showcase(self(ctx), SHOWCASE_DEFAULT, null, null, StringArgumentType.getString(ctx, "from")))))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(SHOWCASE_MIN, 60))
								.executes(ctx -> showcase(self(ctx), IntegerArgumentType.getInteger(ctx, "seconds"), null, null, null))
								.then(Commands.literal("dao")
										.then(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS)
												.executes(ctx -> showcase(self(ctx), IntegerArgumentType.getInteger(ctx, "seconds"),
														StringArgumentType.getString(ctx, "dao"), null, null))))
								.then(Commands.literal("tingkat")
										.then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
												.executes(ctx -> showcase(self(ctx), IntegerArgumentType.getInteger(ctx, "seconds"), null,
														StringArgumentType.getString(ctx, "grade"), null))))
								.then(Commands.literal("dari")
										.then(Commands.argument("from", StringArgumentType.word()).suggests(SPECS)
												.executes(ctx -> showcase(self(ctx), IntegerArgumentType.getInteger(ctx, "seconds"), null, null,
														StringArgumentType.getString(ctx, "from")))))))
				.then(Commands.literal("bebas")
						.then(Commands.literal("nyala").executes(ctx -> bypass(self(ctx), true)))
						.then(Commands.literal("mati").executes(ctx -> bypass(self(ctx), false))))
				.then(Commands.literal("penguasaan")
						.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS_ALL)
								.then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
										.executes(ctx -> mastery(self(ctx), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "level"))))))
				.then(Commands.literal("gerbang").executes(ctx -> gate(self(ctx))))
				.then(Commands.literal("kehancuran")
						.then(Commands.literal("nyala").executes(ctx -> carve(self(ctx), true)))
						.then(Commands.literal("mati").executes(ctx -> carve(self(ctx), false))))
				.then(Commands.literal("audit").executes(ctx -> audit(ctx.getSource())))
				.then(Commands.literal("murid")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(DISCIPLES)
								.executes(ctx -> teachDisciples(self(ctx), StringArgumentType.getString(ctx, "name")))))
				.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
						.executes(ctx -> castOne(self(ctx), StringArgumentType.getString(ctx, "id")))));
		uji.then(Commands.literal("boneka")
				.executes(ctx -> dummies(self(ctx), 1, false))
				.then(Commands.literal("hapus").executes(ctx -> removeDummies(self(ctx))))
				.then(Commands.literal("bos").executes(ctx -> dummies(self(ctx), 1, true)))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 6))
						.executes(ctx -> dummies(self(ctx), IntegerArgumentType.getInteger(ctx, "n"), false))
						.then(Commands.literal("bos").executes(ctx -> dummies(self(ctx), IntegerArgumentType.getInteger(ctx, "n"), true)))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	// ================================================================== learning

	/** Learns every player spec, resets cooldowns and fills qi; {@code counts} adds the count per set and grade. */
	private static int learnAll(ServerPlayer player, boolean counts) {
		CultivationData data = Cultivators.get(player);
		int learned = 0;
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.playerVisible() && data.learn(spec.id())) {
				learned++;
			}
		}
		Techniques.resetCooldowns(player);
		Techniques.refreshUnlocks(player);
		Qi.fill(player);
		Cultivators.syncNow(player);
		Msg.chat(player, Component.translatable(K + "learned", learned, PlayerCaster.known(data), TechniqueRegistry.playerTotal()).withColor(JADE));
		if (counts) {
			printCounts(player);
		}
		return learned;
	}

	/** "Pedang 14+2 · Api 15+2 … total 168" and the count per grade. */
	private static void printCounts(ServerPlayer player) {
		Map<Dao, int[]> perDao = new EnumMap<>(Dao.class);
		int[] universal = new int[2];
		int[] perGrade = new int[TechniqueGrade.values().length];
		int total = 0;
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (!spec.playerVisible()) {
				continue;
			}
			int[] set = spec.dao() == null ? universal : perDao.computeIfAbsent(spec.dao(), dao -> new int[2]);
			set[spec.isLegacy() ? 1 : 0]++;
			perGrade[spec.grade().ordinal()]++;
			total++;
		}
		net.minecraft.network.chat.MutableComponent sets = Component.empty();
		boolean first = true;
		for (Map.Entry<Dao, int[]> entry : perDao.entrySet()) {
			if (!first) {
				sets.append(Component.literal(" · ").withColor(MUTED));
			}
			first = false;
			sets.append(entry.getKey().displayName().copy().withColor(entry.getKey().color()))
					.append(Component.literal(" " + entry.getValue()[0] + "+" + entry.getValue()[1]).withColor(0xE8E8E8));
		}
		if (!first) {
			sets.append(Component.literal(" · ").withColor(MUTED));
		}
		sets.append(Component.translatable("kultivator.teknik.theme.universal").withColor(GOLD))
				.append(Component.literal(" " + universal[0] + "+" + universal[1]).withColor(0xE8E8E8));
		player.sendSystemMessage(sets);
		net.minecraft.network.chat.MutableComponent grades = Component.empty();
		for (TechniqueGrade grade : TechniqueGrade.values()) {
			if (grade.ordinal() > 0) {
				grades.append(Component.literal(" · ").withColor(MUTED));
			}
			grades.append(grade.displayName().copy().withColor(grade.ribbon())).append(Component.literal(" " + perGrade[grade.ordinal()]).withColor(0xE8E8E8));
		}
		player.sendSystemMessage(grades);
		player.sendSystemMessage(Component.translatable(K + "total", total, TechniqueRegistry.all().size()).withColor(GOLD));
	}

	private static int bypass(ServerPlayer player, boolean on) {
		TechniqueRunner.setBypass(player, on);
		Msg.chat(player, Component.translatable(on ? K + "free_on" : K + "free_off").withColor(on ? GOLD : MUTED));
		return 1;
	}

	private static int mastery(ServerPlayer player, String id, int level) {
		CultivationData data = Cultivators.get(player);
		int count = 0;
		if ("semua".equals(id)) {
			for (TechniqueSpec spec : data.learnedSpecs()) {
				Mastery.setLevel(player, spec.id(), level);
				count++;
			}
		} else {
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec == null) {
				Msg.chat(player, Component.translatable("kultivator.teknik.cmd.unknown", id).withColor(REFUSE));
				return 0;
			}
			Mastery.setLevel(player, spec.id(), level);
			count = 1;
		}
		Msg.chat(player, Component.translatable(K + "mastery", count, Mastery.levelName(level)).withColor(JADE));
		return count;
	}

	/** Dewa Abadi Puncak, ascended, comprehension 90 in the main Dao and every Dewa prerequisite at level 4. */
	private static int gate(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.dao() == null) {
			data.setDao(Dao.SWORD);
		}
		data.setAscended(true);
		Progression.setRank(player, new Rank(Realm.TRUE_IMMORTAL, 0, Realm.TRUE_IMMORTAL.stages() - 1));
		Dao dao = data.dao();
		if (dao != null && data.comprehension(dao) < 90.0D) {
			data.setComprehension(dao, 90.0D);
		}
		int prereqs = 0;
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.gate() == TechniqueSpec.Gate.NONE) {
				continue;
			}
			for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
				if (TechniqueRegistry.byId(prereq.id()) == null) {
					continue;
				}
				data.learn(prereq.id());
				if (Mastery.level(data, prereq.id()) < 4) {
					Mastery.setLevel(player, prereq.id(), 4);
				}
				prereqs++;
			}
		}
		Techniques.refreshUnlocks(player);
		Qi.fill(player);
		Cultivators.syncNow(player);
		Msg.chat(player, Component.translatable(K + "gate", prereqs).withColor(GOLD));
		return 1;
	}

	private static int carve(ServerPlayer player, boolean on) {
		if (on) {
			CARVE.add(player.getUUID());
		} else {
			CARVE.remove(player.getUUID());
		}
		Component why = dev.kultivator.kehancuran.Kehancuran.selfAllowed(player);
		Msg.chat(player, Component.translatable(on ? K + "carve_on" : K + "carve_off").withColor(on ? 0xFF4A3A : MUTED));
		if (on && (why != null || !dev.kultivator.kehancuran.Kehancuran.selfOn(player))) {
			player.sendSystemMessage(Component.translatable(K + "carve_switch").withColor(MUTED));
		}
		return 1;
	}

	// ================================================================== one cast

	/** Learns {@code id} and casts it now (bypass gates, free, forced visual), then prints the numbers and the damage. */
	private static int castOne(ServerPlayer player, String id) {
		TechniqueSpec spec = TechniqueRegistry.byId(id.toLowerCase(Locale.ROOT));
		if (spec == null) {
			Msg.chat(player, Component.translatable("kultivator.teknik.cmd.unknown", id).withColor(REFUSE));
			return 0;
		}
		CultivationData data = Cultivators.get(player);
		if (spec.playerVisible()) {
			data.learn(spec.id());
		}
		Cultivators.dirty(player);
		int mastery = Mastery.level(data, spec.id());
		TechniquePower.Factors f = TechniquePower.factors(data, spec);
		double p = TechniquePower.power(data, spec);
		Msg.chat(player, Component.translatable(K + "cast.title", spec.displayName().copy().withColor(spec.grade().ribbon()), spec.id(),
				spec.grade().displayName(), spec.type().displayName()).withColor(GOLD));
		player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
				"P %s · coef %.2f · M %d · A %.2f · D %.2f · N %.2f · t %d · gap %d · r≤%.0f · targets≤%d · boss %.0f%%",
				Msg.number(p), Loadout.coefficient(spec), mastery, f.a(), f.d(), f.n(), f.t(), f.tierGap(), TechniquePower.radiusCap(spec.dewa()),
				TechniquePower.maxTargets(spec.dewa()), TechniquePower.bossShare(data, spec.grade()) * 100.0D)).withColor(0xE8E8E8));
		if (spec.hasDestruction()) {
			player.sendSystemMessage(Component.translatable(K + "cast.kh", String.format(Locale.ROOT, "%.2f", TechniquePower.kScale(data)),
					TechniquePower.blockCap(data), Component.translatable(kehancuranTests(player) && dev.kultivator.kehancuran.Kehancuran.selfOn(player)
							? "kultivator.teknik.cmd.on" : "kultivator.teknik.cmd.off")).withColor(MUTED));
		}
		PENDING.put(player.getUUID(), new Tally(player.getUUID(), spec.id(), player.level().getGameTime()));
		boolean cast = TechniqueRunner.cast(player, spec, CastOptions.TEST);
		if (!cast) {
			PENDING.remove(player.getUUID());
			player.sendSystemMessage(Component.translatable(K + "cast.refused").withColor(REFUSE));
			return 0;
		}
		PENDING.remove(player.getUUID());
		return 1;
	}

	/** Prints finished tallies: area, duration, count and the damage per target. */
	private static void tickTallies(MinecraftServer server, long now) {
		for (int i = TALLIES.size() - 1; i >= 0; i--) {
			Tally tally = TALLIES.get(i);
			Caster cast = tally.cast;
			boolean done = cast == null || cast.ended || now - tally.created >= TALLY_TICKS;
			if (!done) {
				continue;
			}
			TALLIES.remove(i);
			ServerPlayer player = server.getPlayerList().getPlayer(tally.owner);
			if (player == null) {
				continue;
			}
			if (cast != null) {
				player.sendSystemMessage(Component.translatable(K + "cast.shape",
						Double.isNaN(cast.lastAreaRadius) ? "-" : String.format(Locale.ROOT, "%.1f", cast.lastAreaRadius),
						TechniqueRunner.seconds(Math.max(0L, now - tally.created)), cast.bonusCount, String.format(Locale.ROOT, "%.2f", cast.areaBonus),
						String.format(Locale.ROOT, "%.2f", cast.durationBonus)).withColor(MUTED));
			}
			if (tally.byTarget.isEmpty()) {
				player.sendSystemMessage(Component.translatable(K + "cast.no_hits").withColor(MUTED));
				continue;
			}
			double sum = 0.0D;
			for (Map.Entry<UUID, double[]> entry : tally.byTarget.entrySet()) {
				double[] v = entry.getValue();
				sum += v[1];
				boolean capped = tally.bosses.contains(entry.getKey()) && v[1] + 0.5D < v[0];
				player.sendSystemMessage(Component.literal(" • ").withColor(MUTED).append(tally.names.getOrDefault(entry.getKey(), Component.literal("?")))
						.append(Component.literal(String.format(Locale.ROOT, "  %s → %s  (×%d)", Msg.number(v[0]), Msg.number(v[1]), (int) v[2])).withColor(0xE8E8E8))
						.append(capped ? Component.literal(" ").append(Component.translatable(K + "cast.boss_cap").withColor(0xFFC27A)) : Component.empty()));
			}
			player.sendSystemMessage(Component.translatable(K + "cast.sum", tally.byTarget.size(), Msg.number(sum)).withColor(GOLD));
		}
	}

	// ================================================================== showcase

	private static int showcase(ServerPlayer player, int seconds, @Nullable String daoId, @Nullable String gradeId, @Nullable String from) {
		List<TechniqueSpec> list = new ArrayList<>();
		Dao dao = daoId == null || "universal".equals(daoId) ? null : Dao.byId(daoId);
		if (daoId != null && !"universal".equals(daoId) && dao == null) {
			Msg.chat(player, Component.translatable("kultivator.teknik.cmd.unknown", daoId).withColor(REFUSE));
			return 0;
		}
		TechniqueGrade grade = gradeId == null ? null : TechniqueGrade.byId(gradeId);
		if (gradeId != null && grade == null) {
			Msg.chat(player, Component.translatable("kultivator.teknik.cmd.unknown", gradeId).withColor(REFUSE));
			return 0;
		}
		boolean started = from == null;
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (!started) {
				if (!spec.id().equals(from)) {
					continue;
				}
				started = true;
			}
			if (daoId != null && ("universal".equals(daoId) ? spec.dao() != null : spec.dao() != dao)) {
				continue;
			}
			if (grade != null && spec.grade() != grade) {
				continue;
			}
			if (spec.type() == TechniqueType.PASIF || !spec.playerVisible()) {
				continue;
			}
			list.add(spec);
		}
		if (list.isEmpty()) {
			Msg.chat(player, Component.translatable(K + "show.empty").withColor(REFUSE));
			return 0;
		}
		SHOWCASES.put(player.getUUID(), new Showcase(list, Math.max(SHOWCASE_MIN, seconds), player.level().dimension(), player.level().getGameTime() + 10L));
		Msg.chat(player, Component.translatable(K + "show.start", list.size(), seconds).withColor(GOLD));
		return list.size();
	}

	private static int stopShowcase(ServerPlayer player, boolean tell) {
		Showcase show = SHOWCASES.remove(player.getUUID());
		if (show == null) {
			if (tell) {
				Msg.chat(player, Component.translatable(K + "show.none").withColor(MUTED));
			}
			return 0;
		}
		TechniqueRunner.endAll(player, EndReason.CANCELLED);
		if (tell) {
			Msg.chat(player, Component.translatable(K + "show.stopped", show.index, show.list.size()).withColor(MUTED));
		}
		return 1;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!TALLIES.isEmpty()) {
			tickTallies(server, now);
		}
		if (SHOWCASES.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Showcase> entry : List.copyOf(SHOWCASES.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Showcase show = entry.getValue();
			if (player == null) {
				SHOWCASES.remove(entry.getKey());
				continue;
			}
			if (player.isDeadOrDying() || !player.level().dimension().equals(show.dimension)) {
				stopShowcase(player, true);
				continue;
			}
			long time = player.level().getGameTime();
			if (time < show.next) {
				continue;
			}
			if (show.index >= show.list.size()) {
				SHOWCASES.remove(entry.getKey());
				Msg.chat(player, Component.translatable(K + "show.done", show.list.size()).withColor(GOLD));
				continue;
			}
			TechniqueSpec spec = show.list.get(show.index++);
			TechniqueRunner.endAll(player, EndReason.REPLACED);
			Forms.end(player, EndReason.REPLACED);
			Qi.fill(player);
			int ticks = Math.min(SHOWCASE_MAX_TICKS, Math.max(show.seconds * 20, timeline(spec) + 20));
			show.next = time + ticks;
			Msg.title(player, spec.displayName().copy().withColor(spec.grade().ribbon()), spec.source().copy().withColor(0xC9B98F), 5, Math.max(20, ticks - 15), 10);
			Msg.actionBar(player, Component.literal(show.index + "/" + show.list.size() + "  ").withColor(GOLD)
					.append(spec.grade().displayName().copy().withColor(spec.grade().ribbon())).append(Component.literal(" · ").withColor(MUTED))
					.append(spec.type().displayName().copy().withColor(MUTED)));
			try {
				TechniqueRunner.cast(player, spec, CastOptions.SHOWCASE);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: showcase cast of {} failed", spec.id(), exception);
				Msg.chat(player, Component.translatable(K + "show.failed", spec.id()).withColor(REFUSE));
			}
		}
	}

	/** Ticks a spec's timeline needs: charge, the last step tick and channel/toggle allowances. */
	private static int timeline(TechniqueSpec spec) {
		int ticks = spec.charge() == null ? 0 : spec.charge().ticks();
		int last = 0;
		for (Step step : spec.timeline()) {
			last = Math.max(last, step.at() + Math.max(0, step.repeat() - 1) * Math.max(0, step.every()));
		}
		if (spec.channel() != null || spec.toggle() != null) {
			last = Math.max(last, 100);
		}
		return ticks + last;
	}

	// ================================================================== disciples and dummies

	/** Teaches the named disciples (or all) every spec they qualify for. */
	public static int teachDisciples(ServerPlayer player, String name) {
		List<DiscipleEntity> found = TechniqueCommands.disciples(player, name);
		if (found.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.teknik.cmd.no_disciple", name).withColor(REFUSE));
			return 0;
		}
		for (DiscipleEntity entity : found) {
			DiscipleData data = entity.data();
			int count = 0;
			for (TechniqueSpec spec : TechniqueRegistry.all()) {
				if (DiscipleCultivation.specQualifies(data, spec) && data.learn(spec.id())) {
					data.setCooldown(spec.id(), 0);
					count++;
				}
			}
			data.setQi(DiscipleCultivation.maxQi(data));
			entity.refreshSynced();
			Msg.chat(player, Component.translatable(K + "disciple", entity.getName(), count, data.learnedIds().size()).withColor(JADE));
		}
		return found.size();
	}

	private static int dummies(ServerPlayer player, int count, boolean boss) {
		int spawned = Dummies.spawn(player, count, boss);
		Msg.chat(player, Component.translatable(boss ? K + "dummy.spawned_boss" : K + "dummy.spawned", spawned).withColor(JADE));
		return spawned;
	}

	private static int removeDummies(ServerPlayer player) {
		int removed = Dummies.removeOf(player.getUUID(), player.level().getServer());
		Msg.chat(player, Component.translatable(K + "dummy.removed", removed).withColor(MUTED));
		return removed;
	}

	// ================================================================== audit (22.2.3)

	/** The registry checks: ids, net ids, lang keys, source, FX, prerequisites, ECHO and caps. */
	private static int audit(CommandSourceStack source) {
		List<Component> problems = new ArrayList<>();
		List<Component> warnings = new ArrayList<>();
		JsonObject en = lang("en_us");
		JsonObject id = lang("id_id");
		if (en == null || id == null) {
			warnings.add(Component.translatable(K + "audit.no_lang"));
		}
		Set<String> ids = new HashSet<>();
		Set<Integer> nets = new HashSet<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			String name = spec.id();
			if (!ids.add(name)) {
				problems.add(Component.translatable(K + "audit.dup_id", name));
			}
			if (!nets.add(spec.netId())) {
				problems.add(Component.translatable(K + "audit.dup_net", name, spec.netId()));
			}
			if (en != null && id != null) {
				for (String key : List.of(spec.nameKey(), spec.descKey(), spec.sourceKey(), spec.loreKey())) {
					for (JsonObject lang : List.of(en, id)) {
						String value = string(lang, key);
						if (value == null) {
							problems.add(Component.translatable(K + "audit.lang", name, key, lang == en ? "en_us" : "id_id"));
						} else if (key.equals(spec.sourceKey()) && value.isBlank()) {
							problems.add(Component.translatable(K + "audit.source", name));
						}
					}
				}
			}
			if (!spec.isLegacy()) {
				if (spec.fx(Phase.CAST).isEmpty()) {
					problems.add(Component.translatable(K + "audit.cast_fx", name));
				}
				if (spec.type() == TechniqueType.PAMUNGKAS && !hasShake(spec)) {
					problems.add(Component.translatable(K + "audit.shake", name));
				}
			}
			for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
				if (TechniqueRegistry.byId(prereq.id()) == null) {
					problems.add(Component.translatable(K + "audit.prereq", name, prereq.id()));
				}
			}
			if (!spec.echoAllowed()) {
				for (Bonus bonus : spec.peak()) {
					if (bonus.kind() == Bonus.Kind.ECHO) {
						warnings.add(Component.translatable(K + "audit.echo", name));
					}
				}
			}
			double cap = TechniquePower.radiusCap(spec.dewa());
			for (Step step : spec.timeline()) {
				checkCaps(spec, step.shape(), cap, warnings);
			}
			// DESIGN 27.1 S2: phases nobody sends, ignored template parameters, missing models, an ultimate without a climax
			for (String warning : SpecLint.check(spec)) {
				warnings.add(Component.translatable(K + "audit.polish", name, warning));
			}
		}
		int total = TechniqueRegistry.all().size();
		int players = TechniqueRegistry.playerTotal();
		for (Component line : problems) {
			source.sendSuccess(() -> Component.literal(" ✘ ").withColor(REFUSE).append(line.copy().withColor(0xE8E8E8)), false);
		}
		for (Component line : warnings) {
			source.sendSuccess(() -> Component.literal(" ! ").withColor(0xFFC27A).append(line.copy().withColor(MUTED)), false);
		}
		if (problems.isEmpty()) {
			source.sendSuccess(() -> Msg.prefixed(Component.translatable(K + "audit.ok", total, players).withColor(JADE)), false);
		} else {
			source.sendSuccess(() -> Msg.prefixed(Component.translatable(K + "audit.bad", problems.size(), warnings.size(), total, players)
					.withStyle(ChatFormatting.RED)), false);
		}
		return problems.isEmpty() ? 1 : 0;
	}

	private static boolean hasShake(TechniqueSpec spec) {
		for (List<FxLayer> layers : spec.fx().values()) {
			for (FxLayer layer : layers) {
				if (layer.template() == FxLayer.Template.SHAKE) {
					return true;
				}
			}
		}
		return false;
	}

	/** Warns when a radius above the hard cap is declared (it is clamped; visual size may exceed hit size). */
	private static void checkCaps(TechniqueSpec spec, Object shape, double cap, List<Component> warnings) {
		if (!(shape instanceof Record record)) {
			return;
		}
		for (RecordComponent component : record.getClass().getRecordComponents()) {
			if (component.getType() != Val.class || !component.getName().toLowerCase(Locale.ROOT).contains("radius")) {
				continue;
			}
			try {
				Val val = (Val) component.getAccessor().invoke(record);
				if (val == null || Double.isNaN(val.base())) {
					continue;
				}
				double max = Double.isNaN(val.cap()) ? val.base() : Math.max(val.base(), val.cap());
				if (max > cap + 1.0E-6D) {
					warnings.add(Component.translatable(K + "audit.cap", spec.id(), component.getName(), String.format(Locale.ROOT, "%.1f", max),
							String.format(Locale.ROOT, "%.1f", cap)));
				}
			} catch (ReflectiveOperationException | RuntimeException exception) {
				return;
			}
		}
	}

	private static @Nullable String string(JsonObject lang, String key) {
		JsonElement element = lang.get(key);
		return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
	}

	/** The merged lang file in the jar (read-only). */
	private static @Nullable JsonObject lang(String code) {
		try (InputStream stream = TechniqueTests.class.getResourceAsStream("/assets/kultivator/lang/" + code + ".json")) {
			if (stream == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception exception) {
			Kultivator.LOGGER.warn("Kultivator: could not read lang {} for the audit", code, exception);
			return null;
		}
	}

	private TechniqueTests() {
	}
}

package dev.kultivator.technique.source;

import dev.kultivator.dao.DaoPaths;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.technique.spec.run.Unlocks;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The learning gates every acquisition route of DESIGN 22.8 shares (books, jade slips, flame seeds, scripture pages):
 * realm / layer / stage, Dao, comprehension, mastery prerequisites and the Dewa gate. Pure reads of
 * {@link CultivationData}, so the client tooltips show exactly the server's reasons. Also the one learn flow (chat line,
 * selection, effect, sounds) the routes call once every gate allowed.
 */
public final class LearnRules {

	/** Dewa gate comprehension (22.8.8): in the spec's Dao, the main one for universal specs. */
	public static final double DEWA_COMPREHENSION = 90.0D;
	/** Phase-7 alternative to the rank part of Gerbang Dewa I / II: player stars. */
	public static final int DEWA_STARS_I = 9;
	public static final int DEWA_STARS_II = 18;
	private static final String PREFIX = "kultivator.sumber.";

	/** Grade order of pools and choices: grade, sub-rank, realm gate, then net id. */
	public static final Comparator<TechniqueSpec> GRADE_ORDER = Comparator
			.comparingInt((TechniqueSpec spec) -> spec.grade().tier())
			.thenComparingInt(TechniqueSpec::sub)
			.thenComparingInt(spec -> spec.minRank().step())
			.thenComparingInt(TechniqueSpec::netId);

	// ================================================================== gates

	/** True when the spec is new to the player and every gate allows it now. */
	public static boolean canLearn(CultivationData data, TechniqueSpec spec) {
		return !data.knows(spec.id()) && spec.playerVisible() && missing(data, spec).isEmpty();
	}

	/** True when the spec's Dao is the player's (or it is universal): a Dao art of another path is never for them. */
	public static boolean daoFits(CultivationData data, TechniqueSpec spec) {
		return spec.dao() == null || spec.dao() == data.dao();
	}

	/** What keeps the player from learning {@code spec}, one part per missing gate (empty when nothing does). */
	public static List<Component> missing(CultivationData data, TechniqueSpec spec) {
		List<Component> parts = new ArrayList<>();
		if (!spec.playerVisible()) {
			parts.add(Component.translatable(PREFIX + "missing.disciple_only"));
			return parts;
		}
		Rank need = spec.minRank();
		if (data.rank().step() < need.step()) {
			parts.add(Component.translatable("kultivator.dao.book.missing.realm",
					need.displayName().copy().withColor(need.realm().color()), data.rank().displayName()));
		}
		Dao dao = spec.dao();
		if (dao != null) {
			Dao chosen = data.dao();
			if (chosen != dao) {
				parts.add(chosen == null
						? Component.translatable("kultivator.dao.book.missing.dao_none", DaoPaths.colored(dao))
						: Component.translatable("kultivator.dao.book.missing.dao_other", DaoPaths.colored(dao), DaoPaths.colored(chosen)));
			}
		}
		double comprehension = comprehensionFor(data, spec);
		if (spec.minComprehension() > 0.0D && comprehension + 1.0E-9D < spec.minComprehension() && (dao == null || data.dao() == dao)) {
			parts.add(dao == null
					? Component.translatable(PREFIX + "missing.main_comprehension", format(spec.minComprehension()), format(comprehension))
					: Component.translatable("kultivator.dao.book.missing.comprehension", DaoPaths.colored(dao),
					format(spec.minComprehension()), format(comprehension)));
		}
		for (TechniqueSpec.Prereq prereq : spec.prereqs()) {
			if (Mastery.level(data, prereq.id()) < prereq.level()) {
				TechniqueSpec other = TechniqueRegistry.byId(prereq.id());
				Component name = other == null ? Component.literal(prereq.id()) : name(other);
				parts.add(Component.translatable(PREFIX + "missing.prereq", name, masteryName(prereq.level()),
						masteryName(Mastery.level(data, prereq.id()))));
			}
		}
		// the condition routes of a composite unlock hold for books and slips too (22.9.9 Buka)
		for (dev.kultivator.technique.spec.dsl.Route route : spec.routes()) {
			if (route.kind() == dev.kultivator.technique.spec.dsl.Route.Kind.KNOWN) {
				int known = 0;
				for (TechniqueSpec each : TechniqueRegistry.all()) {
					if (each.playerVisible() && data.knows(each.id())) {
						known++;
					}
				}
				if (known < route.n()) {
					parts.add(Component.translatable(PREFIX + "missing.known", route.n(), known));
				}
			} else if (route.kind() == dev.kultivator.technique.spec.dsl.Route.Kind.SECOND_DAO) {
				double second = Unlocks.secondDao(data);
				if (second + 1.0E-9D < route.n()) {
					parts.add(Component.translatable(PREFIX + "missing.second_dao", route.n(), format(second)));
				}
			}
		}
		if (spec.gate() != TechniqueSpec.Gate.NONE && !dewaGateOpen(data, spec)) {
			parts.add(Component.translatable(PREFIX + "missing.dewa_gate." + (spec.gate() == TechniqueSpec.Gate.DEWA_II ? "2" : "1"),
					format(DEWA_COMPREHENSION), spec.gate() == TechniqueSpec.Gate.DEWA_II ? DEWA_STARS_II : DEWA_STARS_I));
		}
		return parts;
	}

	/**
	 * The rank part of Gerbang Dewa (22.8.8): Dewa Abadi Puncak, ascended, comprehension ≥ 90 in the spec's Dao (main for
	 * universal); or, from phase 7, at least 9 (gate I) / 18 (gate II) player stars. The named prerequisites are separate
	 * lines of {@link #missing}.
	 */
	public static boolean dewaGateOpen(CultivationData data, TechniqueSpec spec) {
		int starsNeeded = spec.gate() == TechniqueSpec.Gate.DEWA_II ? DEWA_STARS_II : DEWA_STARS_I;
		if (TechniquePower.playerStars(data) >= starsNeeded) {
			return true;
		}
		Rank rank = data.rank();
		boolean peak = rank.realm() == Realm.TRUE_IMMORTAL && rank.stage() >= Rank.PEAK;
		return peak && data.ascended() && comprehensionFor(data, spec) + 1.0E-9D >= DEWA_COMPREHENSION;
	}

	/** Comprehension that counts for {@code spec}: its Dao's, or the main one for universal specs. */
	public static double comprehensionFor(CultivationData data, TechniqueSpec spec) {
		return spec.dao() == null ? data.mainComprehension() : data.comprehension(spec.dao());
	}

	// ================================================================== text

	/** The spec's name in its Dao colour (gold for universal specs). */
	public static MutableComponent name(TechniqueSpec spec) {
		return spec.displayName().copy().withColor(color(spec));
	}

	public static int color(TechniqueSpec spec) {
		return spec.dao() == null ? TechniqueBooks.UNIVERSAL_COLOR : spec.dao().color();
	}

	/** "Bumi Tinggi · Area" (grade, sub-rank and type names of the teknik fragment). */
	public static MutableComponent gradeLine(TechniqueSpec spec) {
		return Component.translatable(PREFIX + "grade_line", spec.grade().displayName(), TechniqueGrade.subName(spec.sub()),
				spec.type().displayName()).withColor(spec.grade().ribbon());
	}

	/** Mastery level name 0-4 (Pemula ... Puncak Jurus). */
	public static Component masteryName(int level) {
		return Component.translatable(PREFIX + "mastery." + Math.max(0, Math.min(4, level)));
	}

	public static String format(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	// ================================================================== learning

	/**
	 * Learns {@code spec} for the player (no gate checks: the route checked them): the chat line naming the route
	 * ({@code how}: book, slip, seed, scripture, inheritance), the description and Sumber, a first selection, the learning
	 * effect and sounds; then the automatic unlocks refresh (collector advancements, selection). True when it was new.
	 */
	public static boolean learn(ServerPlayer player, TechniqueSpec spec, String how) {
		CultivationData data = Cultivators.get(player);
		if (!data.learn(spec.id())) {
			return false;
		}
		MutableComponent name = name(spec).withStyle(ChatFormatting.BOLD);
		Msg.chat(player, Component.translatable(PREFIX + "learned." + how, name, gradeLine(spec)).withStyle(ChatFormatting.YELLOW));
		Msg.chat(player, spec.description().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		Msg.chat(player, spec.source().copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (data.selectedId().isEmpty()) {
			data.setSelectedId(spec.id());
			Msg.chat(player, Component.translatable(PREFIX + "selected", spec.displayName(),
					Component.keybind("key.kultivator.cast").withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
		}
		Msg.actionBar(player, Component.translatable(PREFIX + "learned_bar", spec.displayName()).withColor(color(spec)));
		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.DAO_CHOSEN, spec.dao() == null ? -1 : spec.dao().ordinal(), TechniqueBooks.FX_LEARNED, 0.0F);
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.1F);
		level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
		Cultivators.dirty(player);
		Unlocks.refresh(player);
		Cultivators.syncNow(player);
		return true;
	}

	private LearnRules() {
	}
}

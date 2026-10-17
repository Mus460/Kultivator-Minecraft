package dev.kultivator.sect.event;

import dev.kultivator.disciple.DiscipleData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single-elimination bracket (Kompetisi Daftar Kuat, Turnamen Elit, Perang Seratus Dinasti): seeds placed so the two
 * strongest can only meet in the final, rounds built as the winners come in, and an optional bout for third place
 * between the two semi-final losers (played before the final). Session data.
 */
final class Bracket {

	static final int DISCIPLE = 0;
	static final int RIVAL = 1;
	static final int CHAMPION = 2;
	static final int YOUTH = 3;

	/** One fighter: one of the master's disciples (by soul) or a visitor whose data lives here. */
	static final class Entrant {
		final int kind;
		final @Nullable UUID soul;
		final Component name;
		final DiscipleData data;
		final int skin;
		final boolean slim;
		final int power;
		int wins;
		/** The youth's Tekad (war only). */
		int tekad;

		Entrant(int kind, @Nullable UUID soul, Component name, DiscipleData data, int skin, boolean slim, int power) {
			this.kind = kind;
			this.soul = soul;
			this.name = name;
			this.data = data;
			this.skin = skin;
			this.slim = slim;
			this.power = power;
		}

		boolean disciple() {
			return kind == DISCIPLE;
		}
	}

	static final class Match {
		final Entrant a;
		final Entrant b;
		@Nullable Entrant winner;
		final boolean bronze;

		Match(Entrant a, Entrant b, boolean bronze) {
			this.a = a;
			this.b = b;
			this.bronze = bronze;
		}

		@Nullable Entrant loser() {
			return winner == null ? null : winner == a ? b : a;
		}
	}

	final List<List<Match>> rounds = new ArrayList<>();
	private final boolean withBronze;
	@Nullable Match bronze;

	/** {@code seeds} strongest first; its size must be a power of two (2 or more). */
	Bracket(List<Entrant> seeds, boolean withBronze) {
		this.withBronze = withBronze;
		int[] order = seedOrder(seeds.size());
		List<Match> first = new ArrayList<>();
		for (int i = 0; i + 1 < order.length; i += 2) {
			first.add(new Match(seeds.get(order[i] - 1), seeds.get(order[i + 1] - 1), false));
		}
		rounds.add(first);
	}

	/** Bracket positions of seeds 1..n (n a power of two): 1 and 2 can only meet in the final. */
	static int[] seedOrder(int n) {
		int[] order = {1, 2};
		while (order.length < n) {
			int size = order.length * 2;
			int[] next = new int[size];
			for (int i = 0; i < order.length; i++) {
				next[2 * i] = order[i];
				next[2 * i + 1] = size + 1 - order[i];
			}
			order = next;
		}
		return order;
	}

	/** The smallest power of two of at least {@code n} (at least 2). */
	static int slotsFor(int n) {
		int slots = 2;
		while (slots < n) {
			slots *= 2;
		}
		return slots;
	}

	/** The next bout to fight (the bronze bout before the final), or null when everything is decided. */
	@Nullable Match next() {
		List<Match> last = rounds.get(rounds.size() - 1);
		if (last.size() == 1 && bronze != null && bronze.winner == null) {
			return bronze;
		}
		for (Match match : last) {
			if (match.winner == null) {
				return match;
			}
		}
		return null;
	}

	/** Records the result and builds the next round once the current one is complete. */
	void report(Match match, Entrant winner) {
		match.winner = winner;
		winner.wins++;
		List<Match> last = rounds.get(rounds.size() - 1);
		for (Match each : last) {
			if (each.winner == null) {
				return;
			}
		}
		if (last.size() == 1) {
			return;
		}
		if (last.size() == 2 && withBronze) {
			bronze = new Match(last.get(0).loser(), last.get(1).loser(), true);
		}
		List<Match> next = new ArrayList<>();
		for (int i = 0; i + 1 < last.size(); i += 2) {
			next.add(new Match(last.get(i).winner, last.get(i + 1).winner, false));
		}
		rounds.add(next);
	}

	boolean done() {
		return next() == null;
	}

	@Nullable Entrant first() {
		List<Match> last = rounds.get(rounds.size() - 1);
		return done() && last.size() == 1 ? last.get(0).winner : null;
	}

	@Nullable Entrant second() {
		List<Match> last = rounds.get(rounds.size() - 1);
		return done() && last.size() == 1 ? last.get(0).loser() : null;
	}

	@Nullable Entrant third() {
		return bronze == null ? null : bronze.winner;
	}

	/** Who wins a bout nobody fights (visitors among themselves): the stronger is more likely. */
	static Entrant simulate(Match match, RandomSource random) {
		double chance = Math.max(0.1D, Math.min(0.9D, 0.5D + 0.08D * (match.a.power - match.b.power)));
		return random.nextDouble() < chance ? match.a : match.b;
	}

	/** The bracket for the Arena tablet: rounds of {a, b, ak, bk, w (-1 open, 0 a, 1 b)} plus the bronze bout. */
	CompoundTag forClient(@Nullable Match current) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (List<Match> round : rounds) {
			ListTag matches = new ListTag();
			for (Match match : round) {
				matches.add(matchTag(match, current));
			}
			CompoundTag wrapper = new CompoundTag();
			wrapper.put("m", matches);
			list.add(wrapper);
		}
		tag.put("rounds", list);
		tag.putInt("size", rounds.isEmpty() ? 0 : rounds.get(0).size() * 2);
		if (bronze != null) {
			tag.put("bronze", matchTag(bronze, current));
		}
		return tag;
	}

	private static CompoundTag matchTag(Match match, @Nullable Match current) {
		CompoundTag tag = new CompoundTag();
		tag.store("a", ComponentSerialization.CODEC, match.a.name);
		tag.store("b", ComponentSerialization.CODEC, match.b.name);
		tag.putInt("ak", match.a.kind);
		tag.putInt("bk", match.b.kind);
		tag.putInt("w", match.winner == null ? -1 : match.winner == match.a ? 0 : 1);
		tag.putBoolean("now", match == current);
		return tag;
	}
}

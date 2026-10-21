package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One formation puzzle of a Gua Leluhur (DESIGN 34.5.5 P1-P6), also played on the puzzle floors of the tower. Parts
 * are entities (rule W2); a wrong move never kills: the host's backlash costs {@code puzzleBacklash} of max health
 * (leaving at least 1 HP), and the puzzle resets. Each puzzle lasts at most {@link #TIME_LIMIT} ticks per attempt; a
 * hint shows after {@code hintSeconds}. Arrangements come from the room's seed, so a room is the same puzzle every time.
 */
public abstract class Puzzle {

	/** Three minutes per attempt. */
	public static final int TIME_LIMIT = 20 * 60 * 3;

	public enum Kind {
		BAGUA, UNSUR, BINTANG, ILUSI, CERMIN, TIMBANGAN;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return null;
		}
	}

	protected final PuzzleHost host;
	protected final RandomSource random;
	protected boolean done;

	protected Puzzle(PuzzleHost host) {
		this.host = host;
		this.random = RandomSource.create(host.seed());
	}

	/** Makes a puzzle of {@code kind}. */
	public static Puzzle create(Kind kind, PuzzleHost host) {
		return switch (kind) {
			case BAGUA -> new BaguaPuzzle(host);
			case UNSUR -> new UnsurPuzzle(host);
			case BINTANG -> new BintangPuzzle(host);
			case ILUSI -> new IlusiPuzzle(host);
			case CERMIN -> new CerminPuzzle(host);
			case TIMBANGAN -> new TimbanganPuzzle(host);
		};
	}

	public abstract Kind kind();

	/** Spawns the parts (once). */
	public abstract void build();

	/** Every server tick while players are near. */
	public abstract void tick(long now);

	/** Back to the start of an attempt (after a wrong move or the time running out). */
	public abstract void reset();

	/** 0..1 for the bar. */
	public abstract float progress();

	/** A player clicked a part of this puzzle. */
	public void use(ServerPlayer player, TekaPartEntity part, boolean attack) {
	}

	/** The attempt started (the first challenger walked in). */
	public void started() {
	}

	/** The hint time passed. */
	public void hint() {
	}

	/** The whole puzzle ends (room solved or taken down). */
	public void dispose() {
	}

	public boolean done() {
		return done;
	}

	/** The line on the bar. */
	public Component objective() {
		return Component.translatable("kultivator.reruntuhan.teka." + kind().id() + ".objective");
	}

	/** {@code kultivator.reruntuhan.teka.<id>.riddle} (the stele) and {@code .hint}. */
	public Component riddle() {
		return Component.translatable("kultivator.reruntuhan.teka." + kind().id() + ".riddle");
	}

	public Component hintText() {
		return Component.translatable("kultivator.reruntuhan.teka." + kind().id() + ".hint");
	}

	/** Marks the puzzle solved and tells the host once. */
	protected void solve() {
		if (!done) {
			done = true;
			host.solved(this);
		}
	}

	/** The attempt's time ran out: a backlash for everyone inside, then a reset. */
	public void wrongAll() {
		wrong(null);
	}

	/** {@code uji gua selesai} and the tower's test: solves at once. */
	public void forceSolve() {
		solve();
	}

	/** A wrong move: backlash for {@code player} (or everyone inside when null), then a reset. */
	protected void wrong(@Nullable ServerPlayer player) {
		if (player != null) {
			host.backlash(player, this);
		} else {
			for (ServerPlayer each : host.challengers()) {
				host.backlash(each, this);
			}
		}
		reset();
	}

	/** A part entity made with the puzzle's listener and centre. */
	protected @Nullable TekaPartEntity part(net.minecraft.world.entity.EntityType<TekaPartEntity> type, int role, Vec3 at, float yaw) {
		TekaPartEntity part = host.spawn(type, at, yaw);
		if (part == null) {
			return null;
		}
		part.setRole(role);
		part.setCenter(host.frame().centre());
		part.setListener(this::use);
		return part;
	}

	/** A small effect at a part. */
	protected void fx(Vec3 at, int kind, int a, int b, float f) {
		RFx.at(host.level(), at, kind, a, b, f);
	}
}

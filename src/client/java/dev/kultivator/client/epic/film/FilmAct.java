package dev.kultivator.client.epic.film;

import dev.kultivator.technique.spec.dsl.Phase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One act of a film (DESIGN 30.2.5, 30.2.6): consecutive shots that start at an anchor. Between acts the camera is the
 * player's own, and every act re-checks every gate before it starts (30.2.2). Anchors:
 * <ul>
 * <li>{@link Anchor#CAST}: ticks after the arming event (the CAST, or the CHARGE of a charged spec);</li>
 * <li>{@link Anchor#CLIMAX}: ticks after the climax: the server's climax event when it arrives, else the planned
 * {@code climaxIn}; a negative offset (K − n) plans a pre-climax act, which needs a known climax time;</li>
 * <li>{@link Anchor#EVENT}: the {@code index}-th event (1-based) of {@code phase};</li>
 * <li>{@link Anchor#BEAT}: a driver beat ({@code FilmDirector.cue}).</li>
 * </ul>
 * {@link #climb()} marks a long journey act that "Perjalanan langit" governs (Pertama, Selalu, Mati).
 */
public final class FilmAct {

	public enum Anchor { CAST, CLIMAX, EVENT, BEAT }

	final Anchor anchor;
	final int offset;
	final @Nullable Phase phase;
	final int index;
	final @Nullable String beat;
	final List<FilmShot> shots = new ArrayList<>();
	boolean climb;
	private FilmShot.Mask nextMask = FilmShot.Mask.NONE;

	private FilmAct(Anchor anchor, int offset, @Nullable Phase phase, int index, @Nullable String beat) {
		this.anchor = anchor;
		this.offset = offset;
		this.phase = phase;
		this.index = index;
		this.beat = beat;
	}

	public static FilmAct at(Anchor anchor, int offset) {
		return new FilmAct(anchor, offset, null, 0, null);
	}

	/** {@code offset} ticks after the arming event. */
	public static FilmAct cast(int offset) {
		return at(Anchor.CAST, offset);
	}

	/** {@code offset} ticks after (negative: before) the climax. */
	public static FilmAct climax(int offset) {
		return at(Anchor.CLIMAX, offset);
	}

	/** The {@code index}-th (1-based) event of {@code phase}, plus {@code offset}. */
	public static FilmAct event(Phase phase, int index, int offset) {
		return new FilmAct(Anchor.EVENT, offset, phase, Math.max(1, index), null);
	}

	public static FilmAct beat(String beat, int offset) {
		return new FilmAct(Anchor.BEAT, offset, null, 0, beat);
	}

	/** Adds a shot (it starts when the one before ends). */
	public FilmAct shot(FilmShot shot) {
		shots.add(shot.mask(nextMask));
		nextMask = FilmShot.Mask.NONE;
		return this;
	}

	/** The next shot starts with a hard cut under {@code mask}. */
	public FilmAct cut(FilmShot.Mask mask) {
		this.nextMask = mask;
		return this;
	}

	/** A long journey act: "Perjalanan langit" decides whether it plays. */
	public FilmAct climb() {
		this.climb = true;
		return this;
	}

	/** The act's length in presentation ticks. */
	public int length() {
		int total = 0;
		for (FilmShot shot : shots) {
			total += shot.ticks;
		}
		return total;
	}

	public Anchor anchor() {
		return anchor;
	}

	public int offset() {
		return offset;
	}

	public List<FilmShot> shots() {
		return shots;
	}
}

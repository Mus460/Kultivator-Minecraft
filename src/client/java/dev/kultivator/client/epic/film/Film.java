package dev.kultivator.client.epic.film;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One film (DESIGN 30.2.5, 30.3): an id (the spec id), its acts, and the planned climax {@code k} (ticks after the arming
 * event; the test timeline of {@code /sinematik film}) or −1 when the climax is player-timed. {@code titah} films are one
 * continuous act within the 29.16 limits (shots ≤ 4 s, film ≤ 12 s); {@code charged} films arm on the CHARGE event.
 */
public final class Film {

	public static final int MAX_CAMERA = 400;
	public static final int MAX_ACT = 160;
	public static final int MAX_ONE_ACT = 240;

	public final String id;
	final List<FilmAct> acts = new ArrayList<>();
	int k = -1;
	boolean titah;
	boolean charged;
	boolean playerTimed;
	/** At most this many EVENT acts play per cast (the own-art template: three form shots). */
	int maxEventActs = 8;
	int tint = 0xFFE8B0;
	/** A {@code /sinematik kosmik3d} preview (DESIGN 32.7): built by the command, never in the catalogue, no camera cap trim. */
	boolean preview;
	/** Where an untracked prop stands, in the aim space (a missing driver or a Minimal cast still frames the beat). */
	final Map<String, double[]> props = new HashMap<>();

	private Film(String id) {
		this.id = id;
	}

	public static Film of(String id) {
		return new Film(id);
	}

	public Film act(FilmAct act) {
		acts.add(act);
		return this;
	}

	/** The planned climax, ticks after the arming event. */
	public Film k(int ticks) {
		this.k = ticks;
		return this;
	}

	/** The climax comes when the player (or the channel) releases it: K-acts wait for the climax event. */
	public Film playerTimed(int testK) {
		this.playerTimed = true;
		this.k = testK;
		return this;
	}

	public Film charged() {
		this.charged = true;
		return this;
	}

	public Film titah() {
		this.titah = true;
		return this;
	}

	public Film tint(int rgb) {
		this.tint = rgb & 0xFFFFFF;
		return this;
	}

	/** The aim-space fallback of prop {@code key} while no driver tracks it. */
	public Film prop(String key, double x, double y, double z) {
		props.put(key, new double[]{x, y, z});
		return this;
	}

	/** Marks a command preview (32.7). */
	public Film preview() {
		this.preview = true;
		return this;
	}

	public Film maxEventActs(int n) {
		this.maxEventActs = Math.max(0, n);
		return this;
	}

	public List<FilmAct> acts() {
		return acts;
	}

	public int plannedK() {
		return k;
	}

	/** Total camera ticks of every act. */
	public int cameraTicks() {
		int total = 0;
		for (FilmAct act : acts) {
			total += act.length();
		}
		return total;
	}
}

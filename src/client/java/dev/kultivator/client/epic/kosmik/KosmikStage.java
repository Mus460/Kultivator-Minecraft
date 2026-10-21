package dev.kultivator.client.epic.kosmik;

import java.util.List;

/**
 * One 3D set of Film Kosmik 3D (DESIGN 32.1, contract C32.1): the Orbit stage (module O) or the Heaven stage (module H).
 * F's {@link Kosmik3D} owns the camera, the pocket, the dome fallback and the submit order; a stage only writes geometry
 * into the {@link StageFrame} it is handed and answers anchors for the film keys.
 * <p>
 * Rules for implementations: client thread only; {@link #prepare} ≤ 4 ms once (meshes are built once per session per
 * size), never in a static initialiser or a constructor; ItemStacks only through {@code ModelFx.stack} on first draw (the
 * frame's {@link StageFrame#item} does that); no allocation per frame in {@link #render}; animation is a pure function of
 * the shot time, the tier and the seed (sizes change only along an authored path, glows breathe by alpha). No method may
 * throw: {@link Kosmik3D} still wraps every call and drops the stage for the session after repeated failures.
 */
public interface KosmikStage {

	/** {@link Kosmik3D.StageId#ORBIT} or {@link Kosmik3D.StageId#SURGA}. */
	Kosmik3D.StageId id();

	/** At film arm or preview: build meshes (once), start any bake; ≤ 4 ms; never throws. */
	void prepare(StageContext ctx);

	/** Meshes built (a bake may still run: the static map is used meanwhile). */
	boolean ready();

	/** The beat ids of 32.2.3 / 32.3.3 (contract C32.6). */
	List<String> beats();

	/**
	 * A shot of {@code beat} starts, {@code ticks} long (after re-timing). {@code continued}: it follows this stage's
	 * previous shot without a camera jump (same pocket), so persistent state (a door already open) carries on.
	 */
	void beat(String beat, StageContext ctx, int ticks, boolean continued);

	/**
	 * The anchor {@code name} at shot time {@code t} (ticks, fractional) of the running beat, written into {@code out}
	 * (origin in stage metres, forward and up unit vectors, scale). False = unknown name (the core uses the stage origin and
	 * logs once).
	 */
	boolean anchor(String name, double t, StageAnchor out);

	/** True when a body of the stage hides stage point P from the eye E (the flare's light point). */
	boolean occluded(double px, double py, double pz, double ex, double ey, double ez);

	/** Every frame while a shot of this stage is bound. */
	void render(StageFrame frame);

	/** The binding ended (cut to another stage or the world, skip, abort, death, disconnect). */
	void end();

	/** Disconnect / resource reload: free dynamic textures and caches. */
	void release();

	/** Lines for {@code /sinematik kosmik3d info}. */
	List<String> info();
}

package dev.kultivator.client.epic.film;

/**
 * The coordinate spaces of film keys (DESIGN 30.2.5). Every key point names one; the space is evaluated per frame, so a
 * moving subject or prop carries the camera with it.
 * <ul>
 * <li>{@link #S} subject: the caster's interpolated feet; z = the body yaw at the act start (forward), x = right, y = up;</li>
 * <li>{@link #A} aim: the aim point; z = from the caster to the aim (at the film start), y = up;</li>
 * <li>{@link #W} world: absolute coordinates (test shots);</li>
 * <li>{@link #P} prop: a tracked prop ({@code FilmDirector.track}); z = its facing, y = up. A missing prop falls back to
 * the aim space;</li>
 * <li>{@link #K} sky: a direction (yaw from the subject's forward, pitch up) at a far radius; only for looks.</li>
 * </ul>
 */
public enum FilmSpace {
	S, A, W, P, K
}

package dev.kultivator.client.epic;

import dev.kultivator.client.epic.render.EpicRenderer;

/**
 * One timed piece of a {@link Scene} (a cue of a storyboard track, DESIGN 23.6): waits {@code delay} real ticks, then
 * lives {@code life} presentation ticks (its age advances by the {@link EpicClock} σ, so slow motion stretches it).
 * Geometry elements draw in {@link #render} while the director grants them geometry this tick, and fall back to
 * particles in {@link #fallback} otherwise. Client thread only.
 */
public abstract class Element {

	/** A large translucent layer keeps its slot in this order (1 keeps longest, 9 drops first); 0 = not a large layer. */
	protected int largeRank;
	protected Scene scene;
	int delay;
	protected float age;
	protected float life;
	boolean started;
	boolean done;
	/** Granted geometry for this tick (scene and large-layer budget). */
	protected boolean geo;

	protected Element(int delay, float life) {
		this.delay = Math.max(0, delay);
		this.life = Math.max(1.0F, life);
	}

	/** Called once when the delay is over. */
	protected void start() {
	}

	/** Every tick while alive (after the age advanced). */
	protected void tick() {
	}

	/** Every tick while alive and not granted geometry (particle fallback). */
	protected void fallback() {
	}

	/** Once when the life is over or the scene ends. */
	protected void end() {
	}

	/** True for elements that draw geometry in {@link #render}. */
	public boolean geometry() {
		return false;
	}

	/** True for soft translucent layers (need "soft geometry"); false for thin glow, bolts and pillars. */
	public boolean soft() {
		return true;
	}

	/** Draws this frame ({@code age} includes the partial tick × σ). */
	public void render(EpicRenderer.Frame frame, float age) {
	}

	/** The age at this frame: the tick age plus the partial tick × σ, never past the life. */
	public float progressAge(float partial) {
		return Math.min(life, age + partial * EpicClock.sigma());
	}

	/** 0..1 of the life. */
	public float progress(float at) {
		return Math.max(0.0F, Math.min(1.0F, at / life));
	}

	/** Fade in over {@code in} ticks and out over the last {@code out} ticks. */
	public float fade(float at, float in, float out) {
		float a = in <= 0.0F ? 1.0F : Math.min(1.0F, at / in);
		float b = out <= 0.0F ? 1.0F : Math.min(1.0F, Math.max(0.0F, (life - at) / out));
		return Math.max(0.0F, Math.min(a, b));
	}

	public boolean alive() {
		return started && !done;
	}

	/** True once the element ended (or was killed with its scene). */
	public boolean finished() {
		return done;
	}

	public boolean granted() {
		return geo;
	}

	/** Ends the element early (the scene keeps running). */
	public void stop() {
		life = Math.min(life, age);
	}

	/** Makes the element live {@code more} presentation ticks longer. */
	public void extend(float more) {
		life = Math.max(life, age + more);
	}

	public int largeRank() {
		return largeRank;
	}

	/** A one-shot action at a delay (cards, sounds, sky grades, camera requests). */
	public static Element at(int delay, Runnable action) {
		return new Element(delay, 1.0F) {
			@Override
			protected void start() {
				action.run();
			}
		};
	}

	/** A per-tick action for {@code ticks} after a delay (particle emitters). */
	public static Element every(int delay, int ticks, Ticker ticker) {
		return new Element(delay, ticks) {
			private int n;

			@Override
			protected void tick() {
				ticker.tick(n++, this);
			}
		};
	}

	/** The body of {@link #every}. */
	@FunctionalInterface
	public interface Ticker {
		void tick(int tick, Element self);
	}
}

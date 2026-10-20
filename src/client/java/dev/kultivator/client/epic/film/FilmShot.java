package dev.kultivator.client.epic.film;

import dev.kultivator.client.epic.sound.FilmSound;
import dev.kultivator.technique.spec.dsl.Dampak;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One film shot of the DSL (DESIGN 30.2.5): {@code FilmShot.of(ticks)} with position keys, a look, FOV and roll curves,
 * camera moves (orbit, aerial, worm, pov, dolly, crash, whip), bullet time, shake, lens layers, Kenney cues, a title card
 * and the cosmic backdrop. Immutable once a {@link FilmAct} holds it (the builders return {@code this}); turned into a
 * camera shot only by {@link FilmDirector}. Every number stays inside the film caps of 30.2.3 (the path is validated and
 * shortened when needed, the look is rate-limited per frame).
 */
public final class FilmShot {

	/** The cut that opens this shot (30.2.3): no blend, hidden by a mask. */
	public enum Mask { NONE, BLACK_DIP, CLOUD_WIPE, IMPACT, SMEAR }

	/** What a dolly keeps at constant screen size (the vertigo effect). */
	public enum Target { SUBJECT, AIM, PROP }

	/** One cue at a shot fraction (or a tick offset when {@code tick} ≥ 0). */
	public record SoundAt(float at, int tick, FilmSound.Cue cue, float pitch, float volume, boolean positioned) {
	}

	/** One FOV or roll key. */
	public record Curve(float t, float value) {
	}

	public final int ticks;
	final List<FilmKey> keys = new ArrayList<>();
	final List<Curve> fov = new ArrayList<>();
	final List<Curve> roll = new ArrayList<>();
	final List<SoundAt> sounds = new ArrayList<>();
	// look
	@Nullable FilmSpace lookSpace;
	@Nullable String lookProp;
	double lx;
	double ly;
	double lz;
	@Nullable FilmSpace lookEndSpace;
	@Nullable String lookEndProp;
	double lex;
	double ley;
	double lez;
	/** K-space look: yaw from the subject's forward and pitch up (degrees), from → to. */
	float skyYaw0 = Float.NaN;
	float skyPitch0;
	float skyYaw1;
	float skyPitch1;
	// moves
	float worm;
	@Nullable String povProp;
	boolean povSubject;
	float povBack;
	float povUp;
	@Nullable Target dolly;
	@Nullable String dollyProp;
	float crashK;
	int crashTicks;
	float whipAt = -1.0F;
	float whipDeg;
	int whipTicks;
	float whipPitch;
	float bulletSigma;
	int bulletTicks;
	@Nullable FilmShake.Profile shake;
	float shakeAmp;
	int shakeTicks;
	// layers
	int lens = FilmLens.mask(FilmLens.LETTERBOX, FilmLens.VIGNETTE);
	int tint = 0xFFE8B0;
	int vignetteRgb = 0x000000;
	boolean card;
	boolean cosmic;
	@Nullable Dampak.Vista vista;
	@Nullable FilmSpace flareSpace;
	@Nullable String flareProp;
	double fx;
	double fy;
	double fz;
	/** Holds the last pose up to 20 ticks when the climax is late (the last pre-climax shot). */
	boolean climaxHold;
	Mask mask = Mask.NONE;
	/** Grey grade (stopped time, the life-death disc's death half): the HUD grey wash. */
	float grey;
	/** Ducks the world and plays the cosmic hum at this pitch (0 = none). */
	float quiet;

	private FilmShot(int ticks) {
		this.ticks = Math.max(4, Math.min(120, ticks));
	}

	/** A shot of {@code ticks} (presentation ticks, ≤ 6 s). */
	public static FilmShot of(int ticks) {
		return new FilmShot(ticks);
	}

	// ================================================================ keys

	/** A position key at shot fraction {@code t} in {@code space} (sine in-out to the next key). */
	public FilmShot key(float t, FilmSpace space, double x, double y, double z) {
		keys.add(new FilmKey(t, space, null, x, y, z, Ease.IN_OUT));
		return this;
	}

	/** A position key in the space of prop {@code prop}. */
	public FilmShot keyP(float t, String prop, double x, double y, double z) {
		keys.add(new FilmKey(t, FilmSpace.P, prop, x, y, z, Ease.IN_OUT));
		return this;
	}

	/** Sets the ease of every segment added so far (time is reparameterised per segment). */
	public FilmShot ease(Ease ease) {
		for (int i = 0; i < keys.size(); i++) {
			keys.set(i, keys.get(i).withEase(ease));
		}
		return this;
	}

	/** An orbit around the origin of {@code space}: radius r0 → r1, height h0 → h1, from {@code start}° by {@code sweep}°. */
	public FilmShot orbit(FilmSpace space, @Nullable String prop, double r0, double r1, double h0, double h1, float start, float sweep) {
		int n = Math.max(3, Math.min(9, 2 + (int) Math.ceil(Math.abs(sweep) / 45.0F)));
		for (int i = 0; i < n; i++) {
			float t = i / (float) (n - 1);
			double a = Math.toRadians(start + sweep * t);
			double r = r0 + (r1 - r0) * t;
			double h = h0 + (h1 - h0) * t;
			keys.add(new FilmKey(t, space, prop, Math.sin(a) * r, h, -Math.cos(a) * r, Ease.LINEAR));
		}
		if (lookSpace == null) {
			look(space, prop, 0.0D, 1.2D, 0.0D);
		}
		return this;
	}

	/** An aerial pullback over the origin of {@code space}: height h0 → h1 while the distance grows d0 → d1. */
	public FilmShot aerial(FilmSpace space, @Nullable String prop, double h0, double h1, double d0, double d1) {
		keys.add(new FilmKey(0.0F, space, prop, 0.0D, h0, -d0, Ease.OUT));
		keys.add(new FilmKey(1.0F, space, prop, 0.0D, h1, -d1, Ease.OUT));
		if (lookSpace == null) {
			look(space, prop, 0.0D, 0.0D, 0.0D);
		}
		return this;
	}

	/** Top-down over the origin of {@code space} at height {@code h}, the frame turning by {@code turn}°. */
	public FilmShot topDown(FilmSpace space, @Nullable String prop, double h, float turn) {
		int n = Math.max(2, Math.min(6, 2 + (int) (Math.abs(turn) / 30.0F)));
		for (int i = 0; i < n; i++) {
			float t = i / (float) (n - 1);
			double a = Math.toRadians(turn * t);
			// a small offset keeps the yaw defined while the camera looks almost straight down
			keys.add(new FilmKey(t, space, prop, Math.sin(a) * 0.6D, h, -Math.cos(a) * 0.6D, Ease.LINEAR));
		}
		look(space, prop, 0.0D, 0.0D, 0.0D);
		return this;
	}

	/** A low angle: every key's y is the height above the ground surface (0.2-0.5 b). */
	public FilmShot worm(float height) {
		this.worm = Math.max(0.2F, Math.min(0.5F, height));
		return this;
	}

	/** Rides a prop: {@code back} behind and {@code up} above it, looking along its facing. */
	public FilmShot pov(String prop, float back, float up) {
		this.povProp = prop;
		this.povBack = back;
		this.povUp = up;
		return this;
	}

	/** Rides the subject (its look direction): the subject's own motion is not counted. */
	public FilmShot povSubject(float back, float up) {
		this.povSubject = true;
		this.povBack = back;
		this.povUp = up;
		return this;
	}

	// ================================================================ look

	public FilmShot look(FilmSpace space, @Nullable String prop, double x, double y, double z) {
		this.lookSpace = space;
		this.lookProp = prop;
		this.lx = x;
		this.ly = y;
		this.lz = z;
		return this;
	}

	public FilmShot look(FilmSpace space, double x, double y, double z) {
		return look(space, null, x, y, z);
	}

	/** Looks at a tracked prop (its origin plus {@code up}). */
	public FilmShot lookProp(String prop, double up) {
		return look(FilmSpace.P, prop, 0.0D, up, 0.0D);
	}

	/** The look target at the end of the shot (slerped from the start look). */
	public FilmShot lookEnd(FilmSpace space, @Nullable String prop, double x, double y, double z) {
		this.lookEndSpace = space;
		this.lookEndProp = prop;
		this.lex = x;
		this.ley = y;
		this.lez = z;
		return this;
	}

	/** A sky look: yaw from the subject's forward and pitch up, from → to (degrees). */
	public FilmShot lookSky(float yaw0, float pitch0, float yaw1, float pitch1) {
		this.skyYaw0 = yaw0;
		this.skyPitch0 = pitch0;
		this.skyYaw1 = yaw1;
		this.skyPitch1 = pitch1;
		return this;
	}

	// ================================================================ lens, FOV, roll

	public FilmShot fov(float t, float k) {
		fov.add(new Curve(t, Math.max(0.55F, Math.min(1.45F, k))));
		return this;
	}

	public FilmShot roll(float t, float degrees) {
		roll.add(new Curve(t, Math.max(-20.0F, Math.min(20.0F, degrees))));
		return this;
	}

	/** Keeps {@code target} at its screen size while the camera moves (k = tan(φ₀/2)·d₀ / (tan(φ/2)·d)). */
	public FilmShot dolly(Target target) {
		this.dolly = target;
		return this;
	}

	public FilmShot dollyProp(String prop) {
		this.dolly = Target.PROP;
		this.dollyProp = prop;
		return this;
	}

	/** A crash zoom: FOV to {@code k} in ≤ 4 ticks (expo out) with speed lines. */
	public FilmShot crash(float k, int inTicks) {
		this.crashK = Math.max(0.55F, Math.min(1.45F, k));
		this.crashTicks = Math.max(1, Math.min(4, inTicks));
		this.lens |= FilmLens.SPEED_LINES.bit();
		return this;
	}

	/** A whip pan of {@code degrees} (yaw) over ≤ 6 ticks starting at fraction {@code at}, always with the smear. */
	public FilmShot whip(float at, float degrees, int inTicks) {
		this.whipAt = Math.max(0.0F, Math.min(1.0F, at));
		this.whipDeg = degrees;
		this.whipTicks = Math.max(2, Math.min(6, inTicks));
		this.lens |= FilmLens.SMEAR.bit();
		return this;
	}

	/** A vertical whip (pitch, degrees; negative looks up). */
	public FilmShot whipPitch(float at, float degrees, int inTicks) {
		this.whipAt = Math.max(0.0F, Math.min(1.0F, at));
		this.whipPitch = degrees;
		this.whipTicks = Math.max(2, Math.min(6, inTicks));
		this.lens |= FilmLens.SMEAR.bit();
		return this;
	}

	/** Bullet time from the shot's start: σ for {@code realTicks} (≤ 30). */
	public FilmShot bullet(float sigma, int realTicks) {
		this.bulletSigma = Math.max(0.03F, Math.min(0.3F, sigma));
		this.bulletTicks = Math.max(4, Math.min(30, realTicks));
		return this;
	}

	public FilmShot shake(FilmShake.Profile profile, float amp, int shakeTicks) {
		this.shake = profile;
		this.shakeAmp = Math.max(0.0F, Math.min(1.0F, amp));
		this.shakeTicks = Math.max(1, shakeTicks);
		return this;
	}

	public FilmShot lens(FilmLens... layers) {
		this.lens |= FilmLens.mask(layers);
		return this;
	}

	/** The tint of the flare, anamorphic streak and light leak. */
	public FilmShot tint(int rgb) {
		this.tint = rgb & 0xFFFFFF;
		return this;
	}

	/** The vignette colour (black by default). */
	public FilmShot vignette(int rgb) {
		this.vignetteRgb = rgb & 0xFFFFFF;
		return this;
	}

	/** The light point of the flare and the anamorphic streak (default: the look target). */
	public FilmShot flare(FilmSpace space, @Nullable String prop, double x, double y, double z) {
		this.flareSpace = space;
		this.flareProp = prop;
		this.fx = x;
		this.fy = y;
		this.fz = z;
		this.lens |= FilmLens.FLARE.bit();
		return this;
	}

	/** A cue at shot fraction {@code at} for the protagonist. */
	public FilmShot sound(float at, FilmSound.Cue cue) {
		sounds.add(new SoundAt(at, -1, cue, 1.0F, 1.0F, false));
		return this;
	}

	public FilmShot sound(float at, FilmSound.Cue cue, float pitch) {
		sounds.add(new SoundAt(at, -1, cue, pitch, 1.0F, false));
		return this;
	}

	/** A cue {@code tick} ticks into the shot (the delayed boom after a silence). */
	public FilmShot soundTick(int tick, FilmSound.Cue cue, float pitch) {
		sounds.add(new SoundAt(0.0F, Math.max(0, tick), cue, pitch, 1.0F, false));
		return this;
	}

	/** A positioned cue at the aim (mono cues only; stereo falls back to own). */
	public FilmShot soundAtAim(float at, FilmSound.Cue cue, float pitch) {
		sounds.add(new SoundAt(at, -1, cue, pitch, 1.2F, true));
		return this;
	}

	/** The technique's title card at the shot's start. */
	public FilmShot card() {
		this.card = true;
		this.lens |= FilmLens.TITLE.bit();
		return this;
	}

	/** This shot shows Langit Kosmik (and the spec's vista, or {@code vista} when given). */
	public FilmShot cosmic(@Nullable Dampak.Vista shown) {
		this.cosmic = true;
		this.vista = shown;
		this.quiet = 0.6F;
		return this;
	}

	public FilmShot cosmic() {
		return cosmic(null);
	}

	/** Ducks the world and plays the energy hum at {@code pitch} (space is quiet; stopped time). */
	public FilmShot quiet(float pitch) {
		this.quiet = pitch;
		return this;
	}

	/** A grey grade (α ≤ 0.2). */
	public FilmShot grey(float alpha) {
		this.grey = Math.max(0.0F, Math.min(0.2F, alpha));
		return this;
	}

	/** This shot ends on the climax: it holds its last pose up to 20 ticks when the climax is late. */
	public FilmShot holdForClimax() {
		this.climaxHold = true;
		return this;
	}

	FilmShot mask(Mask cut) {
		this.mask = cut;
		return this;
	}

	public Mask mask() {
		return mask;
	}

	public int lensMask() {
		return lens;
	}

	public boolean isCosmic() {
		return cosmic;
	}
}

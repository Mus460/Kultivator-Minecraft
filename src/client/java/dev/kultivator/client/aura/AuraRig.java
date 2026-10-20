package dev.kultivator.client.aura;

import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.anim.model.ModelRequest;
import dev.kultivator.data.Realm;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The per-entity state of one Aura Kultivasi (DESIGN 31.8 C31.3): its fade, its clocks, its props ({@link ModelHandle}s by
 * key), eased stage values and the driver's scratch data. Everything is driven by client ticks; values asked for during a
 * frame are eased with the frame's partial tick, so sizes glide smoothly and only ever move once per change.
 */
public final class AuraRig {

	/** The animation clock wraps here: divisible by every period and turn rate of 31.4, small enough for float precision. */
	public static final long CYCLE = 504000L;
	/** Ticks between two tries at a refused prop. */
	private static final int PROP_RETRY = 20;

	final int entityId;
	// ---- lifecycle (owned by the engine)
	float raw;
	float rawPrev;
	float hold = 1.0F;
	float holdPrev = 1.0F;
	boolean want;
	boolean wantedBefore;
	boolean started;
	boolean breaking;
	boolean ownPlayer;
	float share = 1.0F;
	@Nullable Realm realm;
	@Nullable AuraRealmDriver driver;
	/** The snapshot of this tick before the engine's decisions (fade, LOD...). */
	@Nullable AuraSubject base;
	/** The snapshot handed to the driver this tick. */
	@Nullable AuraSubject subject;
	int goneTicks;
	int stageKey = Integer.MIN_VALUE;
	int stageChangedAt = Integer.MIN_VALUE;
	float lodNow;
	double distance;
	boolean firstPersonSelf;
	Vec3 lastFeet = Vec3.ZERO;
	/** Props this aura may hold right now (31.2.5; 0 while fading out, reduced, simple, first person...). */
	int propAllowance;
	/** Particles this aura may still make (refilled every tick, 31.2.5). */
	float particleTokens;
	// ---- sound
	@Nullable AuraHum.Loop hum;
	int nextChime;
	int nextBell;
	// ---- clocks
	long gameTime;
	int age;
	float partial;
	// ---- driver state
	private final Map<String, Ease> eases = new HashMap<>();
	private final Map<String, Prop> props = new HashMap<>();
	private final Set<String> wanted = new HashSet<>();
	private final Map<String, Object> data = new HashMap<>();

	private static final class Ease {
		float from;
		float to;
		int start;
		int ticks;

		float value(float now) {
			if (ticks <= 0 || now >= start + ticks) {
				return to;
			}
			float t = Math.max(0.0F, (now - start) / ticks);
			float s = t * t * (3.0F - 2.0F * t);
			return from + (to - from) * s;
		}
	}

	private static final class Prop {
		@Nullable ModelHandle handle;
		int retryAt;
		int createdAt;
		float scale = Float.NaN;
		@Nullable String model;
	}

	AuraRig(int entityId) {
		this.entityId = entityId;
	}

	// ================================================================== clocks

	/** {@code gameTime + entityId × 37} (31.2.2): the deterministic animation clock of this aura. */
	public long clock() {
		return gameTime + entityId * 37L;
	}

	/** The clock with the frame's partial tick, wrapped at {@link #CYCLE} (for angles and phases). */
	public float time() {
		return Math.floorMod(clock(), CYCLE) + partial;
	}

	/** The partial tick of the frame being drawn (0 during a tick). */
	public float partial() {
		return partial;
	}

	/** Ticks this rig has lived (client ticks). */
	public int age() {
		return age;
	}

	/** Ticks since the last stage or layer change ({@link Integer#MAX_VALUE} if none). */
	public int stageChangedAgo() {
		return stageChangedAt == Integer.MIN_VALUE ? Integer.MAX_VALUE : age - stageChangedAt;
	}

	// ================================================================== eased values

	/**
	 * The eased value of {@code key}: the first call answers {@code target}; a new target moves there once over
	 * {@code ticks} (smoothstep), from wherever the value was.
	 */
	public float ease(String key, float target, int ticks) {
		float now = age + partial;
		Ease ease = eases.get(key);
		if (ease == null) {
			ease = new Ease();
			ease.from = target;
			ease.to = target;
			ease.start = age;
			ease.ticks = 0;
			eases.put(key, ease);
			return target;
		}
		if (ease.to != target) {
			ease.from = ease.value(now);
			ease.to = target;
			ease.start = age;
			ease.ticks = Math.max(1, ticks);
		}
		return ease.value(now);
	}

	/** Like {@link #ease} but a value that is new starts at {@code from} (an element appearing at a stage fades in). */
	public float easeFrom(String key, float from, float target, int ticks) {
		if (!eases.containsKey(key)) {
			Ease ease = new Ease();
			ease.from = from;
			ease.to = target;
			ease.start = age;
			ease.ticks = Math.max(1, ticks);
			eases.put(key, ease);
		}
		return ease(key, target, ticks);
	}

	// ================================================================== props

	/**
	 * The prop {@code key}, made once from {@code request} when the aura may hold one more prop (the 31.2.5 budget and
	 * {@code ModelFx}'s own); null when refused (it is tried again 20 ticks later; the driver draws its quad stand-in
	 * meanwhile). Asking again for a live prop just returns it.
	 */
	public @Nullable ModelHandle prop(String key, Supplier<ModelRequest> request) {
		wanted.add(key);
		Prop prop = props.get(key);
		if (prop != null && prop.handle != null) {
			if (prop.handle.alive()) {
				return prop.handle;
			}
			prop.handle = null;
			prop.scale = Float.NaN;
			prop.retryAt = age + PROP_RETRY;
		}
		if (prop == null) {
			prop = new Prop();
			props.put(key, prop);
		}
		if (age < prop.retryAt || liveProps() >= propAllowance || !AuraEngine.takeProp()) {
			return null;
		}
		ModelRequest made = request.get();
		ModelHandle handle = made == null ? null : ModelFx.show(made);
		if (handle == null) {
			AuraEngine.giveBackProp();
			prop.retryAt = age + PROP_RETRY;
			return null;
		}
		prop.handle = handle;
		prop.createdAt = age;
		prop.model = made.id();
		prop.scale = made.scale();
		return handle;
	}

	/** The live prop {@code key} (or null) without asking for it. */
	public @Nullable ModelHandle propIfAlive(String key) {
		Prop prop = props.get(key);
		return prop != null && prop.handle != null && prop.handle.alive() ? prop.handle : null;
	}

	/** True while prop {@code key} is shown. */
	public boolean hasProp(String key) {
		return propIfAlive(key) != null;
	}

	/** Ticks since prop {@code key} was made (−1 when it is not shown). */
	public int propAge(String key) {
		Prop prop = props.get(key);
		return prop != null && prop.handle != null && prop.handle.alive() ? age - prop.createdAt : -1;
	}

	/** Resizes prop {@code key} once over 20 ticks when {@code scale} differs from the size last asked for. */
	public void propScale(String key, float scale) {
		Prop prop = props.get(key);
		if (prop != null && prop.handle != null && prop.handle.alive() && Math.abs(prop.scale - scale) > 1.0E-3F) {
			prop.scale = scale;
			prop.handle.scaleTo(scale, 20);
		}
	}

	/** Shows another model on prop {@code key} once (a lotus opening into the 16-petal one). */
	public void propModel(String key, String model) {
		Prop prop = props.get(key);
		if (prop != null && prop.handle != null && prop.handle.alive() && !model.equals(prop.model)) {
			prop.model = model;
			prop.handle.swap(model);
		}
	}

	public void endProp(String key, int fadeTicks) {
		Prop prop = props.remove(key);
		if (prop != null && prop.handle != null) {
			prop.handle.end(fadeTicks);
			AuraEngine.giveBackProp();
		}
	}

	public void endProps(int fadeTicks) {
		for (Prop prop : props.values()) {
			if (prop.handle != null) {
				prop.handle.end(fadeTicks);
				AuraEngine.giveBackProp();
			}
		}
		props.clear();
	}

	/** Props alive now. */
	int liveProps() {
		int n = 0;
		for (Prop prop : props.values()) {
			if (prop.handle != null && prop.handle.alive()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Starts a sweep (optional, for drivers that ask for their props every tick): props not asked for with {@link #prop}
	 * between this and {@link #sweepEnd} are ended there. The engine never sweeps by itself, so a prop asked for once in
	 * {@code start} stays until it is ended (but the engine ends every prop while the aura may hold none: fading out,
	 * reduced LOD, Sederhana, first person; ask again from {@code tick} to bring them back).
	 */
	public void sweepBegin() {
		wanted.clear();
	}

	/** Ends the props not asked for since {@link #sweepBegin} (a stage that drops a prop). */
	public void sweepEnd(int fadeTicks) {
		for (Iterator<Map.Entry<String, Prop>> it = props.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Prop> entry = it.next();
			if (!wanted.contains(entry.getKey())) {
				Prop prop = entry.getValue();
				if (prop.handle != null) {
					prop.handle.end(fadeTicks);
					AuraEngine.giveBackProp();
				}
				it.remove();
			}
		}
	}

	// ================================================================== scratch data

	/** The driver's scratch value {@code key}, made by {@code init} the first time. */
	@SuppressWarnings("unchecked")
	public <T> T data(String key, Supplier<T> init) {
		Object value = data.get(key);
		if (value == null) {
			value = init.get();
			data.put(key, value);
		}
		return (T) value;
	}

	/** Forgets the eased values and scratch data (a new realm starts fresh). */
	void reset() {
		eases.clear();
		data.clear();
	}

	// ================================================================== fade

	/** The lifecycle fade at the frame's partial tick (31.2.2: smoothstep in 30 t / out 20 t, held at half while breaking through). */
	float fade(float partialTick) {
		float r = rawPrev + (raw - rawPrev) * partialTick;
		float h = holdPrev + (hold - holdPrev) * partialTick;
		return smooth(r) * h;
	}

	float fadeNow() {
		return smooth(raw) * hold;
	}

	static float smooth(float t) {
		float x = Math.max(0.0F, Math.min(1.0F, t));
		return x * x * (3.0F - 2.0F * x);
	}
}

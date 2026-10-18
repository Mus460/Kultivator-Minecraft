package dev.kultivator.client.epic;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.epic.EpicIds;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A running 7b scene (DESIGN 23.6): the storyboard elements of one moment of one subject (or one place), keyed by
 * (moment, subject or position) so later beats attach to it (a STRIKE joins the live vortex of its tribulation). It
 * fades out after 120 s without a beat, on ABORT / STOP, a level change, disconnect or the protagonist's death.
 */
public final class Scene {

	/** The watchdog: a scene without a beat for this long fades out. */
	public static final int WATCHDOG = 2400;

	public final int moment;
	public final int subjectId;
	/** True when the local player is the cue's viewer: only then camera, bars, FULL cards and music. */
	public final boolean own;
	public final int viewerId;
	public final int priority;
	public final boolean client;
	public Vec3 pos;
	public int seed;
	public int s;
	public int flags;
	public int a;
	public int b;
	public float f;
	public int beat = -1;
	/** 1 for the protagonist, "Efek pemain lain" for others (0.25 Ringan), × LOW_LOAD 0.5 for geometry. */
	public float view;
	/** Distance falloff of the scene for the local viewer: 1 within 48 b, 0 at {@code epicRange}. */
	public float falloff = 1.0F;
	/** Storyboard data shared between beats (the tribulation's variant, realm and strike count...). */
	public Object data;
	/** Shared references between beats (the vortex of a tribulation, the gate of an ascension...). */
	public final Map<String, Element> named = new HashMap<>();
	final List<Element> elements = new ArrayList<>();
	private final List<Element> pending = new ArrayList<>();
	int idle;
	boolean ending;
	int endTicks;
	/** True for the one other player's giant scene allowed to draw geometry (at most 25% of the budget). */
	boolean geometryAllowed;
	private final RandomSource random;

	Scene(int moment, int subjectId, boolean own, int viewerId, int priority, boolean client, Vec3 pos, int seed) {
		this.moment = moment;
		this.subjectId = subjectId;
		this.own = own;
		this.viewerId = viewerId;
		this.priority = priority;
		this.client = client;
		this.pos = pos;
		this.seed = seed;
		this.random = RandomSource.create(seed);
		this.view = own ? 1.0F : EffectQuality.othersScale();
	}

	/** Adds an element (it starts after its own delay). Safe while the scene is ticking. */
	public <T extends Element> T add(T element) {
		element.scene = this;
		pending.add(element);
		return element;
	}

	/** Adds an element and remembers it under {@code name} (replacing an older one). */
	public <T extends Element> T add(String name, T element) {
		Element old = named.put(name, element);
		if (old != null && old != element) {
			old.stop();
		}
		return add(element);
	}

	/** A one-shot action after {@code delay} ticks. */
	public void at(int delay, Runnable action) {
		add(Element.at(delay, action));
	}

	/** A named element if it is still alive (or waiting). */
	public @Nullable Element get(String name) {
		Element element = named.get(name);
		return element == null || element.done ? null : element;
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public boolean test() {
		return has(EpicIds.FLAG_TEST);
	}

	/** The subject entity when this client tracks it. */
	public @Nullable Entity subject() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || subjectId < 0) {
			return null;
		}
		return minecraft.level.getEntity(subjectId);
	}

	/** The subject's interpolated feet, or the cue position when the subject is not tracked. */
	public Vec3 feet(float partial) {
		Entity entity = subject();
		if (entity != null && entity.distanceToSqr(pos) < 64.0D * 64.0D) {
			return entity.getPosition(partial);
		}
		return pos;
	}

	/** The subject's body yaw (degrees) or 0. */
	public float yaw() {
		Entity entity = subject();
		if (entity instanceof LivingEntity living) {
			return living.yBodyRot;
		}
		return entity == null ? 0.0F : entity.getYRot();
	}

	/** The subject's height (1.8 for a place). */
	public float height() {
		Entity entity = subject();
		return entity == null ? 1.8F : entity.getBbHeight();
	}

	/** A seeded random of this scene (every viewer builds the same shapes from the cue seed). */
	public RandomSource random() {
		return random;
	}

	/** The scene's size and count multiplier (view × "Skala VFX momen" × LOW_LOAD). */
	public float scale() {
		float load = has(EpicIds.FLAG_LOW_LOAD) ? 0.5F : 1.0F;
		return view * EpicSettings.vfx() * load;
	}

	/** Scene scale S as 0..1 of the table. */
	public float s01() {
		return Mth.clamp(s / 10.0F, 0.0F, 1.0F);
	}

	/** Begins the fade-out: every element ends within {@code ticks}. */
	public void end(int ticks) {
		if (!ending) {
			ending = true;
			endTicks = Math.max(1, ticks);
		}
	}

	public boolean ending() {
		return ending;
	}

	/** Every element of this scene that is alive (render pass). */
	public List<Element> elements() {
		return elements;
	}

	/** Advances the scene one tick; false when it is finished. */
	boolean tick() {
		if (!pending.isEmpty()) {
			elements.addAll(pending);
			pending.clear();
		}
		idle++;
		if (ending && --endTicks <= 0) {
			for (Element element : elements) {
				if (element.started && !element.done) {
					element.done = true;
					safeEnd(element);
				}
			}
			elements.clear();
			return false;
		}
		float sigma = EpicClock.sigma();
		for (int i = 0; i < elements.size(); i++) {
			Element element = elements.get(i);
			if (element.done) {
				continue;
			}
			if (!element.started) {
				if (element.delay > 0) {
					element.delay--;
					continue;
				}
				element.started = true;
				try {
					element.start();
				} catch (RuntimeException exception) {
					EpicDirector.warn("start", exception);
					element.done = true;
					continue;
				}
			}
			element.age += sigma;
			try {
				element.tick();
				if (element.geometry() && !element.geo) {
					element.fallback();
				}
			} catch (RuntimeException exception) {
				EpicDirector.warn("tick", exception);
				element.age = element.life;
			}
			if (element.age >= element.life) {
				element.done = true;
				safeEnd(element);
			}
		}
		elements.removeIf(element -> element.done);
		if (!pending.isEmpty()) {
			return true;
		}
		return !elements.isEmpty() || idle < 40;
	}

	/** Ends everything at once (watchdog, level change, stop). */
	void kill() {
		for (Element element : elements) {
			if (element.started && !element.done) {
				element.done = true;
				safeEnd(element);
			}
		}
		elements.clear();
		pending.clear();
	}

	private static void safeEnd(Element element) {
		try {
			element.end();
		} catch (RuntimeException exception) {
			EpicDirector.warn("end", exception);
		}
	}
}

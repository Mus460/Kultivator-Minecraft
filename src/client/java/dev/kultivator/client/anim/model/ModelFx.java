package dev.kultivator.client.anim.model;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.DisplayKey;
import dev.kultivator.client.fx.spec.PhantomDisplays;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 3D technique props (polish plan section 3, contract C3): our cuboid item models ({@code kultivator:fx/<id>}) shown
 * on client-only item displays from {@code PhantomDisplays}, posed and animated here. Every prop is re-keyed each client
 * tick through {@link DisplayKey}: the entity is moved to the prop's base point ({@code setPos}, lerped by the renderer
 * like the body it rides; a prop that turns with its rider takes the rider's yaw as its entity yaw) and the
 * transformation (the animation offset, turn and size) targets the next tick with a one-tick interpolation, re-armed
 * only when the frame changed. Scale only ever ramps once: grow-in, an optional one-shot swell on an event, fade-out.
 * Each prop costs one display (and
 * one per child part when the budget allows) and counts in the PhantomDisplays caps; models made per tick are capped at
 * 2 / 12 / 32 by quality (Minimal only for CORE props). Nothing here is ever sent to or made by the server.
 */
public final class ModelFx {

	private static final int[] PER_TICK = {2, 12, 32};
	/** A PULSE up to this long is a one-shot swell; a longer one was an idle throb and is held steady. */
	static final int PULSE_MAX_TICKS = 20;
	/** The largest swell (+10%). */
	static final float PULSE_MAX_SWELL = 0.1F;
	private static final float VIEW_RANGE = 2.5F;
	private static final Brightness DIM = new Brightness(11, 15);
	private static final Set<String> WARNED = new HashSet<>();
	private static final Map<String, Boolean> KNOWN = new HashMap<>();
	private static final Map<String, ItemStack> STACKS = new HashMap<>();
	private static final List<Prop> PROPS = new ArrayList<>();
	private static int createdThisTick;

	// ================================================================== API (contract C3)

	/** A stack showing model {@code id} (an unknown id shows the "missing" cube, logged once). Made lazily. */
	public static ItemStack stack(String id) {
		String shown = known(id) ? id : "missing";
		if (!shown.equals(id) && WARNED.add(id)) {
			Kultivator.LOGGER.warn("Kultivator: unknown fx model '{}', showing the fallback cube", id);
		}
		return STACKS.computeIfAbsent(shown, key -> {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, Kultivator.id("fx/" + key));
			return stack;
		});
	}

	/** True when {@code assets/kultivator/items/fx/<id>.json} exists (cached). */
	public static boolean known(@Nullable String id) {
		if (id == null || id.isEmpty() || !id.matches("[a-z0-9_]+")) {
			return false;
		}
		return KNOWN.computeIfAbsent(id, key -> {
			try {
				return Minecraft.getInstance().getResourceManager().getResource(Kultivator.id("items/fx/" + key + ".json")).isPresent();
			} catch (RuntimeException exception) {
				return false;
			}
		});
	}

	/** A prop with one animation over its whole life; null when refused (quality, budget, distance). */
	public static @Nullable ModelHandle show(String id, Vec3 pos, float scale, float yawDeg, int ticks, Anim anim) {
		List<AnimStep> steps = new ArrayList<>(1);
		if (anim != null && anim != Anim.NONE) {
			steps.add(defaults(anim, 0, ticks));
		}
		return show(new ModelRequest(id, pos, scale, yawDeg, 0.0F, 0.0F, ticks, steps, -1, Vec3.ZERO, false, FxModels.info(id).glow(), -1,
				FxLayer.Priority.CORE));
	}

	public static @Nullable ModelHandle show(ModelRequest request) {
		if (request == null || request.pos() == null || !finite(request.pos())) {
			return null;
		}
		if (!allowed(request.priority()) || !PhantomDisplays.canCreate(request.pos())) {
			return null;
		}
		Display.ItemDisplay display = createDisplay(request.id(), request.pos(), request.glow());
		if (display == null) {
			return null;
		}
		createdThisTick++;
		Prop prop = new Prop(request, display);
		// child parts (wings, hands, stoppers): only when there is room to spare
		for (FxModels.Part part : FxModels.parts(request.id())) {
			if (budgetLeft() <= 1 || !PhantomDisplays.canCreate(request.pos())) {
				break;
			}
			Display.ItemDisplay child = createDisplay(part.model(), request.pos(), request.glow());
			if (child != null) {
				createdThisTick++;
				prop.parts.add(new PartProp(part, child));
			}
		}
		prop.key(true);
		PROPS.add(prop);
		return prop;
	}

	/** Props that may still be made this tick. */
	public static int budgetLeft() {
		if (!PhantomDisplays.enabled()) {
			return 0;
		}
		int cap = EffectQuality.minimal() ? PER_TICK[0] : EffectQuality.maximal() ? PER_TICK[2] : PER_TICK[1];
		return Math.max(0, Math.min(cap - createdThisTick, PhantomDisplays.room()));
	}

	/** Ends every prop of a caster (ABORT, a replaced cast). */
	public static void endAll(int casterId) {
		for (Prop prop : PROPS) {
			if (prop.request.casterId() == casterId) {
				prop.end(6);
			}
		}
	}

	/** Live props of a caster showing {@code id} (ArtModels re-uses them instead of stacking copies). */
	static @Nullable Prop find(int casterId, String id) {
		for (Prop prop : PROPS) {
			if (prop.alive() && prop.request.casterId() == casterId && prop.modelId.equals(id)) {
				return prop;
			}
		}
		return null;
	}

	/** Default parameters for the one-animation form of {@link #show}. */
	public static AnimStep defaults(Anim anim, int start, int ticks) {
		return switch (anim) {
			case SPIN -> AnimStep.of(anim, start, ticks, 4.0F, 0.0F);
			case BOB -> AnimStep.of(anim, start, ticks, 0.15F, 40.0F);
			case GROW -> AnimStep.of(anim, start, Math.min(ticks, 12), 0.1F, 0.0F);
			case SHRINK -> AnimStep.of(anim, Math.max(0, ticks - 8), 8, 0.0F, 0.0F);
			case FALL -> AnimStep.of(anim, start, Math.min(ticks, 16), 12.0F, 0.0F);
			case RISE -> AnimStep.of(anim, start, Math.min(ticks, 12), 2.0F, 0.0F);
			case PULSE -> AnimStep.of(anim, start, ticks, 0.08F, 0.0F);
			case FLAP -> AnimStep.of(anim, start, ticks, 30.0F, 10.0F);
			case ORBIT -> AnimStep.of(anim, start, ticks, 1.5F, 90.0F);
			case SWING -> AnimStep.of(anim, start, Math.min(ticks, 10), 120.0F, 0.0F);
			case TOPPLE -> AnimStep.of(anim, start, Math.min(ticks, 10), 0.0F, 0.0F);
			case THRUST -> AnimStep.of(anim, start, Math.min(ticks, 12), 3.0F, 0.0F);
			case OPEN, CLOSE -> AnimStep.of(anim, start, Math.min(ticks, 10), 0.0F, 0.0F);
			case SHATTER -> AnimStep.of(anim, Math.max(0, ticks - 3), 3, 0.0F, 0.0F);
			case STAMP -> AnimStep.of(anim, start, 2, 0.0F, 0.0F);
			case DISSOLVE -> AnimStep.of(anim, Math.max(0, ticks - 10), 10, 0.0F, 0.0F);
			default -> AnimStep.of(anim, start, ticks, 0.0F, 0.0F);
		};
	}

	// ================================================================== the tick

	/** Once per client tick (AnimClient): ages and re-keys every prop. */
	public static void tick(ClientLevel level) {
		createdThisTick = 0;
		for (Iterator<Prop> it = PROPS.iterator(); it.hasNext(); ) {
			Prop prop = it.next();
			if (!prop.tick(level)) {
				prop.discard();
				it.remove();
			}
		}
	}

	/** Drops every prop (world change, disconnect). */
	public static void clear() {
		for (Prop prop : PROPS) {
			prop.discard();
		}
		PROPS.clear();
		KNOWN.clear();
	}

	private static boolean allowed(FxLayer.Priority priority) {
		if (budgetLeft() <= 0) {
			return false;
		}
		if (EffectQuality.minimal()) {
			return priority == FxLayer.Priority.CORE && PhantomDisplays.alive() < 4;
		}
		return !(priority == FxLayer.Priority.EXTRA && !EffectQuality.maximal() && budgetLeft() < 4);
	}

	private static @Nullable Display.ItemDisplay createDisplay(String id, Vec3 pos, boolean glow) {
		Display.ItemDisplay display = PhantomDisplays.create(EntityTypes.ITEM_DISPLAY, pos, VIEW_RANGE);
		if (display == null) {
			return null;
		}
		display.setItemStack(stack(id));
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBrightnessOverride(glow ? Brightness.FULL_BRIGHT : DIM);
		// the first frame is placed by Prop.key (DisplayKey.snap, no interpolation)
		return display;
	}

	private static boolean finite(Vec3 v) {
		return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
	}

	// ================================================================== props

	/** A child display riding a prop. */
	static final class PartProp {
		final FxModels.Part part;
		final Display.ItemDisplay display;
		final DisplayKey pose;

		PartProp(FxModels.Part part, Display.ItemDisplay display) {
			this.part = part;
			this.display = display;
			this.pose = new DisplayKey(display);
		}
	}

	/** One live prop (the ModelHandle). */
	static final class Prop implements ModelHandle {
		final ModelRequest request;
		final Display.ItemDisplay display;
		final DisplayKey pose;
		final List<PartProp> parts = new ArrayList<>(2);
		final List<AnimStep> anims;
		final FxModels.Info info;
		final Vec3 spawn;
		String modelId;
		/**
		 * Ticks since the prop was made, -1 at the first frame. Each tick the entity stands at the base point of
		 * {@code age} and the transformation targets {@code age + 1} (shown one tick later, so both stay in step);
		 * {@link #now()} is the time of the frame last sent, where re-timed values (moveTo, scaleTo, play) start.
		 */
		int age = -1;
		int life;
		/** The entity yaw (degrees): the rider's body yaw while the prop turns with it, else 0 (kept when the rider goes). */
		float entityYaw;
		Vec3 lastBase;
		// eased base values
		Vec3 pos;
		Vec3 posFrom;
		int posStart;
		int posLerp;
		float yaw, pitch, roll;
		float yawFrom, pitchFrom, rollFrom;
		int rotStart, rotLerp;
		float scale, scaleFrom;
		int scaleStart, scaleLerp;
		int follow;
		Vec3 followOffset;
		boolean followYaw;
		int endAt = -1;
		int fade = 6;
		boolean shattered;
		boolean dead;

		Prop(ModelRequest request, Display.ItemDisplay display) {
			this.request = request;
			this.display = display;
			this.pose = new DisplayKey(display);
			this.anims = new ArrayList<>(request.anims() == null ? List.of() : request.anims());
			this.info = FxModels.info(request.id());
			this.modelId = request.id();
			this.spawn = request.pos();
			this.lastBase = request.pos();
			this.pos = request.pos();
			this.posFrom = request.pos();
			this.yaw = this.yawFrom = request.yaw();
			this.pitch = this.pitchFrom = request.pitch();
			this.roll = this.rollFrom = request.roll();
			this.scale = this.scaleFrom = Mth.clamp(request.scale() <= 0.0F ? info.scale() : request.scale(), 0.05F, 40.0F);
			this.life = Math.max(1, request.ticks());
			this.follow = request.follow();
			this.followOffset = request.followOffset() == null ? Vec3.ZERO : request.followOffset();
			this.followYaw = request.followYaw();
		}

		boolean tick(ClientLevel level) {
			if (dead || display.isRemoved()) {
				return false;
			}
			age++;
			if (endAt < 0 && age >= life) {
				fadeFrom(age, fade);
			}
			if (endAt >= 0 && age >= endAt + fade) {
				return false;
			}
			if (follow >= 0) {
				Entity target = level.getEntity(follow);
				if (target == null || target.isRemoved()) {
					fadeFrom(age, 6);
					// stay where the rider was last seen while fading (not back at the spawn point)
					follow = -1;
					pos = posFrom = lastBase;
					posLerp = 0;
				}
			}
			key(false);
			return true;
		}

		/** The time of the frame last sent (the next tick's frame is {@code now() + 1}). */
		int now() {
			return age + 1;
		}

		/**
		 * Moves the entity to the base point of {@code age} and keys the transformation of {@code age + 1}; the first
		 * frame ({@code first}) is placed without interpolation.
		 */
		void key(boolean first) {
			float t = age + 1.0F;
			Vec3 base = basePosition(age);
			lastBase = base;
			float yawNow = ease(yawFrom, yaw, rotStart, rotLerp, t);
			float pitchNow = ease(pitchFrom, pitch, rotStart, rotLerp, t);
			float rollNow = ease(rollFrom, roll, rotStart, rotLerp, t);
			if (followYaw) {
				Entity target = follow < 0 || Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(follow);
				if (target != null) {
					// the prop's own yaw (rotateTo) is relative to the body it rides; the body's turn is the entity yaw, which
					// the renderer lerps in step with the body itself (kept when the rider goes)
					entityYaw = target instanceof LivingEntity living ? living.yBodyRot : target.getYRot();
				}
				yawNow = entityYaw + yawNow;
			}
			float s = ease(scaleFrom, scale, scaleStart, scaleLerp, t);
			Vector3f offset = new Vector3f();
			Vector3f scl = new Vector3f(1.0F, 1.0F, 1.0F);
			Quaternionf local = new Quaternionf();
			boolean point = false;
			Vec3 travel = null;
			for (AnimStep step : anims) {
				float into = t - step.start();
				if (into < 0.0F) {
					if (!holdsBefore(step)) {
						continue;
					}
					// an entry motion that starts later (a staggered copy, an OPEN after a GROW) waits in its first pose
					// instead of showing the finished pose and then jumping back to the start
					into = 0.0F;
				}
				int dur = Math.max(1, step.duration());
				float p = Mth.clamp(into / dur, 0.0F, 1.0F);
				switch (step.kind()) {
					case SPIN -> {
						float deg = step.a() * Math.min(into, dur);
						switch ((int) step.b()) {
							case 1 -> local.rotateX(rad(deg));
							case 2 -> local.rotateZ(rad(deg));
							default -> local.rotateY(rad(deg));
						}
					}
					case BOB -> offset.y += step.a() * Mth.sin((float) (Math.PI * 2.0D * into / Math.max(1.0F, step.b())));
					case GROW -> {
						// one eased ramp, no overshoot (an overshoot reads as the prop growing and shrinking)
						float from = step.a() <= 0.0F || step.a() >= 1.0F ? 0.1F : step.a();
						float g = from + (1.0F - from) * cubic(p);
						scl.mul(g);
					}
					case SHRINK -> scl.mul(Math.max(0.0F, 1.0F - p * p * p));
					// the landing squash is the STAMP step's (both together squashed twice)
					case FALL -> offset.y += step.a() * (1.0F - p * p);
					case RISE -> offset.y -= step.a() * (1.0F - cubic(p));
					case SLIDE -> {
						if (step.to() != null) {
							float k = step.b() == 1.0F ? cubic(p) : p;
							Vec3 from = startOf(step);
							Vec3 at = from.add(step.to().subtract(from).scale(k));
							offset.add((float) (at.x - base.x), (float) (at.y - base.y), (float) (at.z - base.z));
							if (p < 1.0F) {
								travel = step.to().subtract(from);
							}
							point = true;
						}
					}
					case THRUST -> {
						float out = into < dur * 0.6F ? back(into / (dur * 0.6F)) : 1.0F;
						float dist = 0.5F + (step.a() - 0.5F) * out;
						Vec3 fwd = facing(yawNow, pitchNow);
						offset.add((float) fwd.x * dist, (float) fwd.y * dist, (float) fwd.z * dist);
						float fadeStart = dur * 0.6F + 3.0F;
						if (into > fadeStart) {
							scl.mul(Math.max(0.0F, 1.0F - (into - fadeStart) / Math.max(1.0F, dur - fadeStart + 2.0F)));
						}
						point = true;
					}
					case SWING -> {
						float deg;
						if (step.b() > 0.0F) {
							deg = step.a() * 0.5F * Mth.sin((float) (Math.PI * 2.0D * into / step.b()));
						} else {
							float sm = p * p * (3.0F - 2.0F * p);
							deg = -step.a() * 0.5F + step.a() * sm;
						}
						local.rotateX(rad(deg));
					}
					case TOPPLE -> local.rotateX(rad(90.0F * p * p));
					case OPEN -> {
						float w = 0.1F + 0.9F * cubic(p);
						scl.mul(w, 1.0F, w);
					}
					case CLOSE -> {
						float w = 1.0F - 0.9F * p * p * p;
						scl.mul(w, 1.0F, w);
					}
					case SHATTER -> {
						scl.mul(1.0F + 0.3F * Mth.clamp(into / 2.0F, 0.0F, 1.0F));
						if (into >= 2.0F && !shattered) {
							shattered = true;
							burst(base.add(offset.x, offset.y, offset.z), 14);
							endAt = age;
							fade = 1;
						}
					}
					case PULSE -> {
						// one gentle swell on an event (up once, back once); a long PULSE was an idle throb and is held steady
						if (step.duration() <= PULSE_MAX_TICKS) {
							scl.mul(1.0F + Math.min(PULSE_MAX_SWELL, Math.abs(step.a())) * Mth.sin((float) Math.PI * p));
						}
					}
					case FLAP -> local.rotateZ(rad(step.a() * Mth.sin((float) (Math.PI * 2.0D * into / Math.max(2.0F, step.b())))));
					case ORBIT -> {
						double angle = Math.toRadians(step.b() / 20.0D * into);
						offset.add((float) (Math.cos(angle) * step.a()), 0.0F, (float) (Math.sin(angle) * step.a()));
					}
					case LUNGE -> {
						if (step.to() != null) {
							Vec3 dir = step.to().subtract(base);
							double len = dir.length();
							if (len > 1.0E-4D) {
								dir = dir.scale(1.0D / len);
								float tri = into < 4.0F ? into / 4.0F : Math.max(0.0F, 1.0F - (into - 4.0F) / 4.0F);
								float d = (float) Math.min(2.0D, len) * cubic(tri);
								offset.add((float) dir.x * d, (float) dir.y * d, (float) dir.z * d);
							}
						}
					}
					case STAMP -> {
						if (into < 2.0F) {
							scl.mul(1.15F, 0.85F, 1.15F);
						}
					}
					case DISSOLVE -> {
						scl.mul(1.0F - 0.15F * p);
						if (((int) into & 1) == 0 && p < 1.0F) {
							burst(base.add(offset.x, offset.y + 0.5F * s, offset.z), 2);
						}
						if (p >= 1.0F && endAt < 0) {
							endAt = age;
							fade = 2;
						}
					}
					default -> {
					}
				}
			}
			// fade out by shrinking
			if (endAt >= 0) {
				float f = Mth.clamp(1.0F - (t - endAt) / Math.max(1, fade), 0.0F, 1.0F);
				scl.mul(f * f * (3.0F - 2.0F * f));
			}
			if (travel != null && travel.lengthSqr() > 1.0E-6D) {
				// a sliding prop faces where it flies
				yawNow = (float) Math.toDegrees(Math.atan2(-travel.x, travel.z));
				pitchNow = (float) Math.toDegrees(-Math.atan2(travel.y, Math.sqrt(travel.x * travel.x + travel.z * travel.z)));
			}
			Quaternionf rotation = new Quaternionf().rotateY(rad(-yawNow)).rotateX(rad(pitchNow)).rotateZ(rad(rollNow));
			if (point && info.axis() == FxModels.Axis.UP) {
				// swords and staffs fly tip first: their +y turns to the front
				rotation.rotateX(rad(90.0F));
			}
			rotation.mul(local);
			Vector3f scaleVec = new Vector3f(scl).mul(s);
			// the base point is the entity position; the translation carries only the animation offset (world axes here)
			Vector3f translation = new Vector3f(offset);
			send(pose, base, translation, rotation, scaleVec, first);
			for (PartProp part : parts) {
				keyPart(part, base, translation, rotation, scaleVec, t, first);
			}
		}

		/** Holds the first pose before the step starts (entry motions), instead of skipping the step. */
		private static boolean holdsBefore(AnimStep step) {
			return switch (step.kind()) {
				case GROW, OPEN, FALL, RISE -> true;
				case SWING -> step.b() <= 0.0F;
				default -> false;
			};
		}

		/**
		 * Stands the entity on {@code base} turned by {@link #entityYaw} and keys the world-axis frame in the entity's own
		 * frame (the renderer turns the transformation by the entity yaw first).
		 */
		private void send(DisplayKey target, Vec3 base, Vector3f translation, Quaternionf rotation, Vector3f scale, boolean first) {
			Quaternionf undo = new Quaternionf().rotationY(rad(entityYaw));
			Vector3f t = undo.transform(new Vector3f(translation));
			Quaternionf r = new Quaternionf(undo).mul(rotation);
			target.move(base);
			target.turn(entityYaw);
			if (first) {
				target.snap(t, r, scale);
			} else {
				target.key(t, r, scale, 1);
			}
		}

		private void keyPart(PartProp part, Vec3 base, Vector3f translation, Quaternionf rotation, Vector3f scaleVec, float t, boolean first) {
			FxModels.Part def = part.part;
			// model pixels to the display's local frame (the item renderer turns models by 180 degrees about y)
			Vector3f local = new Vector3f(-def.x() / 16.0F, def.y() / 16.0F, -def.z() / 16.0F).mul(scaleVec);
			Vector3f at = rotation.transform(local).add(translation);
			Quaternionf rot = new Quaternionf(rotation);
			float into = t;
			switch (def.anim()) {
				case FLAP -> rot.rotateZ(rad(def.a() * Mth.sin((float) (Math.PI * 2.0D * into / Math.max(2.0F, def.b())))));
				case SPIN -> {
					float deg = def.a() * into;
					switch ((int) def.b()) {
						case 1 -> rot.rotateX(rad(deg));
						case 2 -> rot.rotateZ(rad(deg));
						default -> rot.rotateY(rad(deg));
					}
				}
				case SWING -> rot.rotateX(rad(def.a() * Mth.sin((float) (Math.PI * 2.0D * into / Math.max(2.0F, def.b())))));
				default -> {
				}
			}
			send(part.pose, base, at, rot, scaleVec, first);
		}

		/** The eased (or followed) base position at time {@code t}. */
		private Vec3 basePosition(float t) {
			if (follow >= 0) {
				ClientLevel level = Minecraft.getInstance().level;
				Entity target = level == null ? null : level.getEntity(follow);
				if (target != null) {
					Vec3 off = followOffset;
					if (followYaw) {
						float bodyYaw = target instanceof LivingEntity living ? living.yBodyRot : target.getYRot();
						off = off.yRot((float) Math.toRadians(-bodyYaw));
					}
					return target.position().add(off);
				}
			}
			if (posLerp <= 0 || t >= posStart + posLerp) {
				return pos;
			}
			float k = cubic(Mth.clamp((t - posStart) / posLerp, 0.0F, 1.0F));
			return posFrom.add(pos.subtract(posFrom).scale(k));
		}

		/** Where a SLIDE starts: the base position when the step began. */
		private Vec3 startOf(AnimStep step) {
			return basePosition(step.start());
		}

		private static float ease(float from, float to, int start, int lerp, float t) {
			if (lerp <= 0 || t >= start + lerp) {
				return to;
			}
			float k = cubic(Mth.clamp((t - start) / lerp, 0.0F, 1.0F));
			return Mth.lerp(k, from, to);
		}

		private void burst(Vec3 at, int count) {
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			int n = EffectQuality.scale(count);
			ItemParticleOption shard = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack(modelId)));
			float spread = Math.min(3.0F, 0.3F * scale + 0.2F);
			for (int i = 0; i < n; i++) {
				double vx = (level.getRandom().nextDouble() - 0.5D) * 0.5D;
				double vy = level.getRandom().nextDouble() * 0.35D + 0.05D;
				double vz = (level.getRandom().nextDouble() - 0.5D) * 0.5D;
				level.addParticle(shard, at.x + vx * spread, at.y + vy * spread, at.z + vz * spread, vx, vy, vz);
			}
		}

		void discard() {
			dead = true;
			PhantomDisplays.remove(display);
			for (PartProp part : parts) {
				PhantomDisplays.remove(part.display);
			}
		}

		// ------------------------------------------------------------ ModelHandle

		@Override
		public void moveTo(Vec3 target, int lerpTicks) {
			if (dead || target == null || !finite(target)) {
				return;
			}
			posFrom = basePosition(age);
			pos = target;
			posStart = age;
			posLerp = Math.max(0, lerpTicks);
		}

		@Override
		public void rotateTo(float yaw, float pitch, float roll, int lerpTicks) {
			if (dead || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
				return;
			}
			// turn from the frame last sent (the transformation runs one tick ahead of the age)
			int now = now();
			yawFrom = ease(yawFrom, this.yaw, rotStart, rotLerp, now);
			pitchFrom = ease(pitchFrom, this.pitch, rotStart, rotLerp, now);
			rollFrom = ease(rollFrom, this.roll, rotStart, rotLerp, now);
			// turn the short way round
			this.yaw = yawFrom + Mth.wrapDegrees(yaw - yawFrom);
			this.pitch = pitch;
			this.roll = roll;
			rotStart = now;
			rotLerp = Math.max(0, lerpTicks);
		}

		@Override
		public void scaleTo(float scale, int lerpTicks) {
			if (dead || !Float.isFinite(scale)) {
				return;
			}
			// resize from the size last sent: one continuous ramp, never a jump back to an older size
			int now = now();
			scaleFrom = ease(scaleFrom, this.scale, scaleStart, scaleLerp, now);
			this.scale = Mth.clamp(scale, 0.05F, 40.0F);
			scaleStart = now;
			scaleLerp = Math.max(0, lerpTicks);
		}

		@Override
		public void play(AnimStep step) {
			if (dead || step == null) {
				return;
			}
			int start = now() + Math.max(0, step.start());
			if (step.kind() == Anim.PULSE) {
				// swells never stack or restart: a swell already under way plays out
				for (AnimStep running : anims) {
					if (running.kind() == Anim.PULSE && running.duration() <= PULSE_MAX_TICKS && start < running.start() + running.duration()) {
						return;
					}
				}
			} else if (step.kind() == Anim.SWING) {
				// a new swing replaces the old one (two swings would add up)
				anims.removeIf(running -> running.kind() == Anim.SWING);
			}
			anims.add(new AnimStep(step.kind(), start, step.duration(), step.a(), step.b(), step.to()));
			life = Math.max(life, start + step.duration() + 2);
		}

		@Override
		public void follow(int entityId, Vec3 offset, boolean yaw) {
			if (dead) {
				return;
			}
			follow = entityId;
			followOffset = offset == null ? Vec3.ZERO : offset;
			followYaw = yaw;
		}

		@Override
		public void swap(String modelId) {
			if (dead || modelId == null || modelId.equals(this.modelId)) {
				return;
			}
			this.modelId = modelId;
			display.setItemStack(stack(modelId));
		}

		@Override
		public void end(int fadeTicks) {
			// the fade starts with the next frame
			fadeFrom(now(), fadeTicks);
		}

		/** Starts the one fade-out ramp at age {@code at} (a later call never restarts it). */
		private void fadeFrom(int at, int fadeTicks) {
			if (dead || endAt >= 0) {
				return;
			}
			endAt = at;
			fade = Math.max(1, fadeTicks);
		}

		@Override
		public boolean alive() {
			return !dead && endAt < 0;
		}

		/** Lengthens the prop's life to at least {@code ticks} more. */
		void extend(int ticks) {
			if (!dead && endAt < 0) {
				life = Math.max(life, age + ticks);
			}
		}
	}

	// ================================================================== maths

	static float rad(float degrees) {
		return degrees * Mth.DEG_TO_RAD;
	}

	static float cubic(float t) {
		float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}

	/** easeOutBack(1.4). */
	static float back(float t) {
		float x = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
		float c1 = 1.4F;
		return 1.0F + (c1 + 1.0F) * x * x * x + c1 * x * x;
	}

	/** The world direction a yaw/pitch (Minecraft degrees) looks along. */
	static Vec3 facing(float yaw, float pitch) {
		return Vec3.directionFromRotation(pitch, yaw);
	}

	/** The Minecraft yaw that looks from {@code from} to {@code to}. */
	public static float yawTo(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	public static float pitchTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		return (float) Math.toDegrees(-Math.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)));
	}

	/** The identifier of a model's item definition (for checks). */
	public static Identifier id(String model) {
		return Kultivator.id("fx/" + model);
	}

	private ModelFx() {
	}
}

package dev.kultivator.client.fx.spec;

import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.quad.BeamQuad;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The phantom display templates of DESIGN 22.4.3, drawn with client-only Display entities from {@link PhantomDisplays}:
 * GIANT_ITEM (≤ 24), GIANT_BLOCK (≤ 24 per side), LASER (a quad beam since the polish pass; a stretched block when the
 * beam cap is full), PETAL_RING (≤ 16 slabs opening, spinning, flat or tilted), GLYPH (billboard text, ≤ 40) and
 * SWORD_RING (≤ 24 orbiting items, riding the caster when attached to it), plus the polish MODEL template (a 3D prop
 * through the animation module's ModelFx). Each display is re-keyed every tick through {@link DisplayKey} (the next tick's
 * frame with a one-tick interpolation, re-armed only when the frame changed; the first frame placed without one): fall,
 * rise, spin, grow (one eased ramp), orbit, topple, slide, swing; the last 6 ticks (or a cut's length) shrink to nothing.
 * <p>
 * Polish S15: displays face the viewer by default (item sprites billboard), FACE_FORWARD turns a model along the caster's
 * look, BLADE keeps the old 45° sword tip, UPRIGHT stands flags upright, FLAT lays discs and seals face up; SWING and
 * TOPPLE turn about the side axis, a {@code swingEvery} repeats the swing and an IMPACT of the same cast re-swings it;
 * caster-attached displays follow the caster's turn. S6: a layer above the caster's tier is drawn smaller with fewer
 * copies. S10: a FLAG_PRE_IMPACT fall lands on the event's second point exactly when the event's duration ends. S20: when
 * no display can be made the layer draws its fallback template (authored, else SHELL; swords keep SWORD_SHAPE).
 */
final class DisplayRun extends LayerRun {

	private final FxLayer.Template template;
	private final Display[] displays;
	/** The keyer of each display (same index; see {@link DisplayKey}). */
	private final DisplayKey[] keys;
	/** The age each display was made at (its first frame is placed then, so it is not keyed again that tick). */
	private final int[] born;
	/** The fade length of a cut run (ABORT, followUntil), fixed when the cut first arrives. */
	private int endTotal = 1;
	private final Vec3 origin;
	private final float[] scatter;
	/** The glyph text split by code point (GLYPH with n > 1 shows one per display). */
	private final int[] glyphs;
	private final FxLayer.Facing facing;
	private final boolean preImpact;
	private final boolean model;
	private final List<ModelHandle> handles = new ArrayList<>();
	private ParticleRun fallback;
	private boolean started;
	private final int modelCopies;
	private boolean largeTaken;
	/** The tick the current swing started (repeats and IMPACT accents restart it). */
	private int swingStart;

	/** True when the spec lays a square FIELD (its corner copies turn by 45°). */
	private static boolean squareField(dev.kultivator.data.TechniqueSpec spec) {
		for (dev.kultivator.technique.spec.dsl.Step step : spec.timeline()) {
			if (step.shape() instanceof dev.kultivator.technique.spec.dsl.Shape.Field field
					&& field.form() == dev.kultivator.technique.spec.dsl.Shape.Field.Form.SQUARE) {
				return true;
			}
		}
		return false;
	}

	DisplayRun(FxEvent ev, FxLayer layer, int life, int index) {
		this(ev, layer, life, index, 1.0F);
	}

	DisplayRun(FxEvent ev, FxLayer layer, int life, int index, float tierScale) {
		super(ev, layer, life, index, tierScale);
		this.template = layer.template();
		this.model = template == FxLayer.Template.MODEL || template == FxLayer.Template.GIANT_ITEM && layer.hasModel();
		this.facing = layer.facing() == null ? (model ? FxLayer.Facing.FACE_FORWARD : FxLayer.Facing.FACE_VIEWER) : layer.facing();
		this.preImpact = ev.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		this.glyphs = layer.text().isEmpty() ? new int[0] : layer.text().codePoints().toArray();
		int wanted = switch (template) {
			case PETAL_RING -> Math.max(1, Math.min(16, layer.n() > 0 ? layer.n() : 8));
			case SWORD_RING -> Math.max(1, Math.min(24, layer.n() > 0 ? layer.n() : 8));
			case GIANT_ITEM, GIANT_BLOCK, MODEL -> Math.max(1, Math.min(8, Math.max(layer.n(), ev.clientRingCopies())));
			// n > 1 splits the text: one glyph per display (誅 戮 陷 絕 at the corners, the eight trigrams around a circle)
			case GLYPH -> layer.n() > 1 ? Math.max(1, Math.min(8, Math.min(layer.n(), Math.max(1, glyphs.length)))) : 1;
			default -> 1;
		};
		if (tierScale < 1.0F && wanted > 1) {
			wanted = Math.max(1, wanted / 2);
		}
		Vec3 base = anchor();
		if (preImpact && ev.hasTarget && layer.attach() == FxLayer.Attach.POS) {
			// the falling prop lands on the event's second point (S10)
			base = ev.target.add(0.0D, layer.offsetY(), 0.0D);
		}
		if (layer.perTarget() && ev.targetId >= 0) {
			base = targetPoint().add(0.0D, layer.offsetY(), 0.0D);
		}
		this.origin = base;
		int granted = Math.min(wanted, Math.min(ev.displayAllowance, ev.displaysLeft));
		if (template == FxLayer.Template.LASER) {
			granted = laserQuad() ? 0 : granted;
		}
		ev.displaysLeft -= granted;
		this.displays = new Display[model ? 0 : granted];
		this.keys = new DisplayKey[displays.length];
		this.born = new int[displays.length];
		this.scatter = new float[Math.max(1, granted) * 2];
		float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : ev.clientRing() ? ev.aux / 100.0F : 4.0F * ev.sizeF;
		boolean several = template == FxLayer.Template.GIANT_ITEM || template == FxLayer.Template.GIANT_BLOCK || template == FxLayer.Template.GLYPH
				|| template == FxLayer.Template.MODEL;
		// With a radius, the copies stand on a ring (corners of a square field, emitters of a circle); without one, giant
		// copies stand scattered within 4 blocks and fall one after another.
		boolean ring = several && granted > 1 && (layer.radius() > 0.0F || ev.clientRing());
		double turn = ring && squareField(ev.spec) ? Math.PI / 4.0D : 0.0D;
		for (int i = 0; i < granted; i++) {
			double angle;
			double rr;
			if (ring) {
				angle = Math.PI * 2.0D * i / granted + turn;
				rr = r * (squareField(ev.spec) ? Math.sqrt(2.0D) : 1.0D);
			} else {
				angle = rnd.nextDouble() * Math.PI * 2.0D;
				rr = granted > 1 && several && template != FxLayer.Template.GLYPH ? r * Math.sqrt(rnd.nextDouble()) : 0.0D;
			}
			scatter[i * 2] = (float) (Math.cos(angle) * rr);
			scatter[i * 2 + 1] = (float) (Math.sin(angle) * rr);
		}
		this.modelCopies = granted;
		if (model) {
			// the props appear when the layer's delay is over (first update)
			if (!AnimBridge.modelKnown(layer.model()) || granted <= 0) {
				fallback = new ParticleRun(ev, standIn(), life, index, tierScale);
			}
		} else if (granted == 0 && template != FxLayer.Template.LASER) {
			fallback = new ParticleRun(ev, standIn(), life, index, tierScale);
		} else if (granted == 0) {
			// the laser became a quad beam: a light spark sheath only
			fallback = new ParticleRun(ev, FxLayer.of(FxLayer.Template.BEAM_CORE, layer.color()).color2(layer.color2()).count(Math.max(8, layer.count() / 4))
					.ticks(layer.ticks()).attach(layer.attach()).above(layer.offsetY()).width(layer.width()).priority(FxLayer.Priority.DETAIL), life, index,
					tierScale);
		}
	}

	/** A LASER drawn as a quad beam (polish S14); false when the beam cap is full. */
	private boolean laserQuad() {
		if (ev.viewScale <= 0.3F || !QuadFx.room(QuadFx.Kind.BEAM)) {
			return false;
		}
		Vec3 start = lineStart();
		Vec3 end = lineEnd(anchor());
		float width = Math.max(0.15F, layer.width() > 0.0F ? layer.width() * Math.min(1.5F, ev.sizeF) : 0.6F) * tierScale;
		int color = layer.color() == 0x101018 || Qi.luma(layer.color()) < 0.15F ? Qi.lerp(layer.color2(), 0x5A3FD0, 0.3F) : layer.color();
		BeamQuad beam = new BeamQuad(start, end, width * 0.6F, color, Qi.core(layer.color2() == layer.color() ? color : layer.color2()), Math.max(6, life), true);
		if (layer.attach() == FxLayer.Attach.CASTER || layer.atHand()) {
			beam.fromHand(ev.casterId);
		}
		return QuadFx.add(beam.caster(ev.casterId).delay(Math.max(0, layer.delay())));
	}

	/** The particle template drawn when no display may be made (S20). */
	private FxLayer standIn() {
		FxLayer.Template stand = layer.fallback();
		if (stand == null) {
			stand = switch (template) {
				case GIANT_ITEM, SWORD_RING -> swordItem() ? FxLayer.Template.SWORD_SHAPE : FxLayer.Template.SHELL;
				case MODEL -> modelHint();
				case LASER -> FxLayer.Template.BEAM_CORE;
				case PETAL_RING -> FxLayer.Template.PETALS;
				case GLYPH -> FxLayer.Template.RUNE_CIRCLE;
				default -> FxLayer.Template.SHELL;
			};
		}
		int color = layer.color() == 0xFFFFFF ? 0xE8F2FF : layer.color();
		FxLayer base = FxLayer.of(stand, color).color2(layer.color2()).count(Math.max(12, layer.count())).ticks(layer.ticks())
				.attach(layer.attach()).above(layer.offsetY()).priority(layer.priority());
		if (stand == FxLayer.Template.SWORD_SHAPE) {
			base = base.size(Math.max(1.0F, Math.min(8.0F, layer.scale() / 2.0F))).variant(layer.motion() == FxLayer.Motion.FALL
					? FxLayer.Variant.FALLING : FxLayer.Variant.NONE);
		}
		if (stand == FxLayer.Template.PALM || stand == FxLayer.Template.FIST) {
			base = base.radius(Math.max(1.0F, Math.min(24.0F, layer.scale())));
			if (layer.motion() == FxLayer.Motion.FALL) {
				base = base.falling().fall(layer.motionA(), layer.motionB());
			}
		} else if (layer.radius() > 0.0F) {
			base = base.radius(layer.radius());
		} else if (stand == FxLayer.Template.SHELL) {
			base = base.radius(Math.max(1.0F, Math.min(8.0F, layer.scale() * 0.6F)));
		}
		if (layer.length() > 0.0F) {
			base = base.length(layer.length());
		}
		return base;
	}

	/** True when the layer shows a sword (its stand-in is then a sword shape). */
	private boolean swordItem() {
		if (layer.item() == FxLayer.HELD) {
			return true;
		}
		return layer.item() == null || new ItemStack(layer.item()).is(net.minecraft.tags.ItemTags.SWORDS);
	}

	/** A particle stand-in from the model's name (the model module's own hint is client data M2 cannot see). */
	private FxLayer.Template modelHint() {
		String id = layer.model();
		if (id.contains("sword") || id.contains("blade") || id.contains("spear") || id.contains("needle")) {
			return FxLayer.Template.SWORD_SHAPE;
		}
		if (id.contains("palm") || id.contains("hand") || id.contains("finger")) {
			return FxLayer.Template.PALM;
		}
		if (id.contains("fist") || id.contains("foot")) {
			return FxLayer.Template.FIST;
		}
		if (id.contains("lotus") || id.contains("petal")) {
			return FxLayer.Template.PETALS;
		}
		if (id.contains("phoenix") || id.contains("dragon") || id.contains("tiger") || id.contains("wolf") || id.contains("kunpeng")) {
			return FxLayer.Template.BEAST;
		}
		if (id.contains("seal") || id.contains("disc") || id.contains("ring") || id.contains("wheel") || id.contains("clock")) {
			return FxLayer.Template.RUNE_CIRCLE;
		}
		return FxLayer.Template.SHELL;
	}

	/** The item a GIANT_ITEM / SWORD_RING display shows: the layer's, or the caster's held item for {@code FxLayer.HELD}. */
	private ItemStack itemFor() {
		if (layer.item() == FxLayer.HELD) {
			Entity caster = ev.caster();
			if (caster instanceof net.minecraft.world.entity.LivingEntity living && !living.getMainHandItem().isEmpty()) {
				return living.getMainHandItem().copyWithCount(1);
			}
			if (caster instanceof DiscipleEntity) {
				// disciples hold nothing visible: a spirit sword of light (the model module's light_sword when present)
				ItemStack light = AnimBridge.modelKnown("light_sword") ? AnimBridge.modelStack("light_sword") : null;
				if (light != null) {
					return light;
				}
			}
			return new ItemStack(Items.IRON_SWORD);
		}
		return new ItemStack(layer.item() == null ? Items.IRON_SWORD : layer.item());
	}

	// ================================================================== MODEL

	private void showModels(int granted) {
		String id = layer.model();
		if (!AnimBridge.modelKnown(id) || granted <= 0) {
			fallback = new ParticleRun(ev, standIn(), life, 0, tierScale);
			return;
		}
		float scale = (layer.scale() > 0.0F ? layer.scale() : 1.0F) * Math.min(1.4F, ev.sizeF) * tierScale;
		float yaw = yawFor(facing);
		// the model module turns the front (north) up with a positive pitch (ModelFx: rotateY(-yaw).rotateX(pitch))
		float pitch = facing == FxLayer.Facing.FLAT ? 90.0F : 0.0F;
		float roll = facing == FxLayer.Facing.BLADE ? (layer.motion() == FxLayer.Motion.FALL || layer.variant() == FxLayer.Variant.FALLING ? 180.0F : 0.0F) : 0.0F;
		boolean follow = attach() == FxLayer.Attach.CASTER;
		for (int i = 0; i < granted; i++) {
			Vec3 at = position(i, 0);
			// each copy keeps its own ring offset while it rides the caster
			Vec3 followOffset = layer.atHand() ? new Vec3(0.4D, 1.25D, 0.55D)
					: new Vec3(i * 2 + 1 < scatter.length ? scatter[i * 2] : 0.0D, layer.offsetY(), i * 2 + 1 < scatter.length ? scatter[i * 2 + 1] : 0.0D);
			List<AnimStep> steps = animSteps(i, at);
			ModelHandle handle = AnimBridge.model(id, at, scale, yaw, pitch, roll, life, steps, follow ? ev.casterId : -1, followOffset, follow, true,
					ev.casterId, layer.priority());
			if (handle != null) {
				handles.add(handle);
			}
		}
		if (handles.isEmpty()) {
			fallback = new ParticleRun(ev, standIn(), life, 0, tierScale);
		}
	}

	/** The model animation of the layer's motion (polish C3 AnimStep), timed by the event when it is a pre-impact fall. */
	private List<AnimStep> animSteps(int index, Vec3 at) {
		List<AnimStep> steps = new ArrayList<>(3);
		int stagger = displaysStagger(index);
		float a = layer.motionA();
		float b = layer.motionB();
		switch (layer.motion()) {
			case FALL -> {
				int ticks = preImpact && ev.duration > 0 ? Math.max(1, ev.duration - stagger) : Math.max(1, Math.round(b));
				steps.add(AnimBridge.step(Anim.FALL, stagger, ticks, a > 0.0F ? a : 12.0F, 0.0F, null));
				steps.add(AnimBridge.step(Anim.STAMP, stagger + ticks, 3, 1.15F, 0.85F, null));
			}
			case RISE -> steps.add(AnimBridge.step(Anim.RISE, stagger, Math.max(1, Math.round(b)), a > 0.0F ? a : 2.0F, 0.0F, null));
			case SPIN -> steps.add(AnimBridge.step(Anim.SPIN, 0, life, a, 0.0F, null));
			case GROW -> steps.add(AnimBridge.step(Anim.GROW, stagger, Math.max(1, Math.round(b)), Math.max(0.05F, a), 0.0F, null));
			case ORBIT -> steps.add(AnimBridge.step(Anim.ORBIT, 0, life, a, b, null));
			case TOPPLE -> steps.add(AnimBridge.step(Anim.TOPPLE, stagger, Math.max(1, Math.round(a)), 90.0F, 0.0F, null));
			case SLIDE -> {
				int ticks = ev.duration > 0 ? ev.duration : Math.max(1, Math.round(a));
				steps.add(AnimBridge.step(Anim.SLIDE, stagger, ticks, 0.0F, 0.0F, ev.hasTarget ? targetPoint() : at.add(fwd.scale(length(8.0F)))));
			}
			case SWING -> steps.add(AnimBridge.step(Anim.SWING, stagger, Math.max(1, Math.round(b)), a == 0.0F ? 90.0F : a, layer.swingPeriod(), null));
			default -> {
			}
		}
		return steps;
	}

	private int displaysStagger(int index) {
		return Math.max(0, index) * 3;
	}

	/** The yaw (degrees) a prop is turned to for {@code facing}. */
	private float yawFor(FxLayer.Facing mode) {
		return switch (mode) {
			case FACE_VIEWER, UPRIGHT -> {
				Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
				Vec3 d = camera.subtract(origin);
				yield (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			}
			default -> attach() == FxLayer.Attach.CASTER ? ev.casterYaw() : (float) Math.toDegrees(Math.atan2(-hfwd.x, hfwd.z));
		};
	}

	// ================================================================== the tick

	@Override
	boolean update(int age) {
		if (fallback != null) {
			return fallback.drive(age);
		}
		if (model) {
			if (!started) {
				started = true;
				showModels(modelCopies);
				if (fallback != null) {
					return fallback.drive(age);
				}
			}
			for (ModelHandle handle : handles) {
				if (AnimBridge.alive(handle)) {
					return true;
				}
			}
			return age < 2;
		}
		if (!started) {
			boolean all = true;
			for (int i = 0; i < displays.length; i++) {
				if (displays[i] == null) {
					displays[i] = create(i, age);
					all &= displays[i] != null;
				}
			}
			if (displays.length > 0 && displays[0] == null && age > 10) {
				fallback = new ParticleRun(ev, standIn(), life, 0, tierScale);
				return true;
			}
			started = all || age > 10;
		}
		if (layer.swingPeriod() > 0 && layer.motion() == FxLayer.Motion.SWING && age - swingStart >= layer.swingPeriod()) {
			swingStart = age;
		}
		// Every tick: the entity stands where it is at this age (setPos, lerped by the renderer over the next frames) and
		// the transformation targets the next age with a one-tick interpolation, which the display starts at its next tick,
		// so both arrive together.
		boolean moving = template == FxLayer.Template.SWORD_RING || attach() == FxLayer.Attach.CASTER;
		for (int i = 0; i < displays.length; i++) {
			Display display = displays[i];
			if (display == null || display.isRemoved() || keys[i] == null || born[i] == age) {
				continue;
			}
			if (moving) {
				keys[i].move(position(i, age));
			}
			place(keys[i], i, Math.min(life, age + 1), false);
		}
		return true;
	}

	/**
	 * An IMPACT of the same cast: a swinging prop swings again (S15). Nothing else: a prop that swelled on every IMPACT /
	 * PULSE / EXTRA event looked like it grew and shrank at random.
	 */
	void accent() {
		if (layer.motion() != FxLayer.Motion.SWING) {
			return;
		}
		swingStart = Math.max(0, age);
		for (ModelHandle handle : handles) {
			// the same swing the layer authored (repeating when it repeats); the model module replaces the running swing
			AnimBridge.play(handle, AnimBridge.step(Anim.SWING, 0, Math.max(1, Math.round(layer.motionB())), layer.motionA() == 0.0F ? 90.0F
					: layer.motionA(), layer.swingPeriod(), null));
		}
	}

	@Override
	void finish(int ticks) {
		boolean first = endIn < 0;
		super.finish(ticks);
		if (first) {
			endTotal = Math.max(1, endIn);
		}
		for (ModelHandle handle : handles) {
			AnimBridge.end(handle, ticks);
		}
		if (fallback != null) {
			fallback.finish(ticks);
		}
	}

	@Override
	void end() {
		for (Display display : displays) {
			PhantomDisplays.remove(display);
		}
		for (ModelHandle handle : handles) {
			if (AnimBridge.alive(handle)) {
				AnimBridge.end(handle, 0);
			}
		}
		handles.clear();
		if (largeTaken) {
			PhantomDisplays.releaseLarge();
			largeTaken = false;
		}
		if (fallback != null) {
			fallback.end();
		}
	}

	private @Nullable Display create(int index, int age) {
		Vec3 at = position(index, age);
		float range = template == FxLayer.Template.LASER || layer.attach() == FxLayer.Attach.SKY ? 2.0F : 1.2F;
		Display display;
		switch (template) {
			case GIANT_ITEM, SWORD_RING -> {
				Display.ItemDisplay item = PhantomDisplays.create(EntityTypes.ITEM_DISPLAY, at, range);
				if (item == null) {
					return null;
				}
				item.setItemStack(itemFor());
				item.setItemTransform(ItemDisplayContext.FIXED);
				if (template == FxLayer.Template.GIANT_ITEM && facing == FxLayer.Facing.FACE_VIEWER) {
					// item sprites turn to the viewer (S15); the tip still turns within the sprite plane
					item.setBillboardConstraints(Display.BillboardConstraints.CENTER);
				} else if (facing == FxLayer.Facing.UPRIGHT) {
					item.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
				}
				display = item;
			}
			case GLYPH -> {
				Display.TextDisplay text = PhantomDisplays.create(EntityTypes.TEXT_DISPLAY, at, range);
				if (text == null) {
					return null;
				}
				String shown = layer.text().isEmpty() ? "道"
						: displays.length > 1 && index < glyphs.length ? new String(Character.toChars(glyphs[index])) : layer.text();
				text.setText(Component.literal(shown).withColor(layer.color() & 0xFFFFFF));
				text.setBackgroundColor(0);
				text.setBillboardConstraints(facing == FxLayer.Facing.FLAT ? Display.BillboardConstraints.FIXED : Display.BillboardConstraints.CENTER);
				text.setLineWidth(200);
				display = text;
			}
			default -> {
				Display.BlockDisplay block = PhantomDisplays.create(EntityTypes.BLOCK_DISPLAY, at, range);
				if (block == null) {
					return null;
				}
				block.setBlockState(blockState());
				display = block;
			}
		}
		// the first frame (the next age's, like every key) without interpolation: no zoom from the default scale 1
		keys[index] = new DisplayKey(display);
		born[index] = age;
		place(keys[index], index, Math.min(life, age + 1), true);
		return display;
	}

	private BlockState blockState() {
		if (layer.block() != null) {
			return layer.block();
		}
		return switch (template) {
			case LASER -> Blocks.STAINED_GLASS.pick(DyeColor.WHITE).defaultBlockState();
			case PETAL_RING -> Blocks.STAINED_GLASS.pick(DyeColor.PINK).defaultBlockState();
			default -> Blocks.GLASS.defaultBlockState();
		};
	}

	/** Where display {@code index} stands at tick {@code age} (the entity position; offsets go into its translation). */
	private Vec3 position(int index, int age) {
		if (template == FxLayer.Template.SWORD_RING) {
			Vec3 center = attach() == FxLayer.Attach.CASTER ? ev.casterPos().add(0.0D, 1.2D + layer.offsetY(), 0.0D) : origin.add(0.0D, 1.2D, 0.0D);
			float r = (layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.0F * ev.sizeF) * tierScale;
			double angle = Math.PI * 2.0D * index / Math.max(1, displays.length) + age * ringSpeed();
			// orbit(r, 0): banners standing still at the formation corners, no bobbing
			double bob = ringSpeed() == 0.0D ? 0.0D : Math.sin(age * 0.1D + index) * 0.2D;
			return center.add(Math.cos(angle) * r, bob, Math.sin(angle) * r);
		}
		if (template == FxLayer.Template.LASER) {
			return anchor();
		}
		if (attach() == FxLayer.Attach.CASTER) {
			Vec3 at = layer.atHand() ? ev.hand() : ev.casterPos().add(0.0D, layer.offsetY(), 0.0D);
			if (index * 2 + 1 < scatter.length) {
				at = at.add(scatter[index * 2], 0.0D, scatter[index * 2 + 1]);
			}
			return at;
		}
		if (index * 2 + 1 < scatter.length) {
			return origin.add(scatter[index * 2], 0.0D, scatter[index * 2 + 1]);
		}
		return origin;
	}

	/**
	 * Keys display {@code index}'s frame of tick {@code age} (one tick ahead of the run's age), interpolated over one tick;
	 * {@code first} places it without interpolation.
	 */
	private void place(DisplayKey key, int index, int age, boolean first) {
		// one ramp to nothing over the last 6 ticks; a cut run (endIn ticks left, counting down after this update) ramps
		// down over its cut length from where it was, never back up
		float cut = endIn >= 0 ? Math.min(1.0F, Math.max(0.0F, (endIn - 1) / (float) Math.max(1, endTotal))) : 1.0F;
		float fade = Math.min(1.0F, Math.max(0.0F, (life - age) / 6.0F)) * cut;
		float p = motionProgress(displays.length > 1 && template != FxLayer.Template.SWORD_RING && template != FxLayer.Template.PETAL_RING
				? Math.max(0, age - index * 3) : age);
		Vector3f offset = new Vector3f();
		Quaternionf rotation = new Quaternionf();
		Vector3f scale;
		float yaw = baseYaw();
		switch (template) {
			case LASER -> {
				Vec3 start = anchor();
				Vec3 end = lineEnd(start);
				Vec3 delta = end.subtract(start);
				float length = (float) Math.min(128.0D, delta.length());
				float width = Math.max(0.1F, layer.width() > 0.0F ? layer.width() * Math.min(1.5F, ev.sizeF) : 0.6F) * tierScale;
				float grow = Math.min(1.0F, (age + 1) / 4.0F) * fade;
				Vec3 axis = delta.lengthSqr() < 1.0E-6D ? fwd : delta.normalize();
				rotation.rotationTo(0.0F, 0.0F, 1.0F, (float) axis.x, (float) axis.y, (float) axis.z);
				scale = new Vector3f(width * grow, width * grow, length * Math.min(1.0F, (age + 1) / 3.0F));
				offset = rotation.transform(new Vector3f(-width * grow / 2.0F, -width * grow / 2.0F, 0.0F));
			}
			case PETAL_RING -> {
				int count = Math.max(1, displays.length);
				float r = (layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.0F * ev.sizeF) * tierScale;
				float mul = layer.scale() > 1.0F ? layer.scale() : 1.0F;
				float length = Math.min(24.0F, r * mul);
				float width = Math.min(12.0F, length * 0.55F);
				double heading = Math.PI * 2.0D * index / count + spin(age);
				float open = Math.min(1.0F, (age + 1) / 12.0F);
				// a flat ring lies open on the ground; otherwise the petals open from upright to tilted
				float tilt = facing == FxLayer.Facing.FLAT ? (float) Math.toRadians(4.0D) : (float) Math.toRadians(85.0D - 55.0D * open);
				float petalYaw = (float) Math.atan2(-Math.sin(heading), Math.cos(heading));
				rotation.rotateY(petalYaw).rotateZ(tilt);
				scale = new Vector3f(length * fade * (facing == FxLayer.Facing.FLAT ? open : 1.0F), 0.25F, width * fade);
				offset = rotation.transform(new Vector3f(0.0F, 0.0F, -width * fade / 2.0F));
				offset.add((float) Math.cos(heading) * r * 0.15F, layer.offsetY(), (float) Math.sin(heading) * r * 0.15F);
			}
			case GLYPH -> {
				float s = Math.min(40.0F, (layer.scale() > 1.0F ? layer.scale() : 3.0F) * ev.sizeF * tierScale) * fade;
				if (layer.motion() == FxLayer.Motion.GROW) {
					float from = Math.max(0.05F, layer.motionA() / Math.max(0.01F, layer.scale()));
					s *= from + (1.0F - from) * p;
				}
				if (facing == FxLayer.Facing.FLAT) {
					rotation.rotateX((float) Math.toRadians(-90.0D)).rotateZ(spin(age));
				}
				scale = new Vector3f(s, s, s);
				offset.set(0.0F, (layer.offsetY() > 0.0F || facing == FxLayer.Facing.FLAT ? 0.0F : 3.0F) + motionLift(p), 0.0F);
			}
			case SWORD_RING -> {
				float s = Math.min(24.0F, (layer.scale() > 1.0F ? layer.scale() : 1.5F) * ev.sizeF * tierScale) * fade;
				double angle = Math.PI * 2.0D * index / Math.max(1, displays.length) + age * ringSpeed();
				if (ringSpeed() == 0.0D || facing == FxLayer.Facing.UPRIGHT) {
					// a still ring (orbit speed 0) shows its items upright, like planted banners
					rotation.rotateY((float) -angle);
				} else {
					// orbiting swords fly flat, point first along the orbit (S15: FLYING face up)
					rotation.rotateY((float) (-angle - Math.PI / 2.0D)).rotateX((float) Math.toRadians(-90.0D)).rotateZ((float) Math.toRadians(-45.0D));
				}
				scale = new Vector3f(s, s, s);
			}
			case GIANT_BLOCK -> {
				float mul = layer.scale() > 1.0F ? layer.scale() : 1.0F;
				float k = fade * growFactor(p) * tierScale;
				float sx = Math.min(24.0F, layer.sx() * mul * ev.sizeF) * k;
				float sy = Math.min(24.0F, layer.sy() * mul * ev.sizeF) * k;
				float sz = Math.min(24.0F, layer.sz() * mul * ev.sizeF) * k;
				rotation.rotateY(yaw + spin(age));
				applyTilt(rotation, p, age);
				scale = new Vector3f(sx, sy, sz);
				offset = rotation.transform(new Vector3f(-sx / 2.0F, 0.0F, -sz / 2.0F));
				offset.add(motionOffset(p, index));
			}
			default -> {
				float s = Math.min(24.0F, (layer.scale() > 1.0F ? layer.scale() : 4.0F) * ev.sizeF * tierScale) * fade * growFactor(p);
				boolean billboard = facing == FxLayer.Facing.FACE_VIEWER || facing == FxLayer.Facing.UPRIGHT;
				boolean falls = layer.variant() == FxLayer.Variant.FALLING || layer.motion() == FxLayer.Motion.FALL;
				float tip = switch (facing) {
					case BLADE -> falls ? (float) Math.toRadians(-135.0D) : layer.variant() == FxLayer.Variant.FLYING ? (float) Math.toRadians(-45.0D)
							: (float) Math.toRadians(45.0D);
					case UPRIGHT -> (float) Math.toRadians(45.0D);
					// a sword sprite points down when it falls, up otherwise (the item's blade runs along its diagonal)
					default -> falls ? (float) Math.toRadians(-135.0D) : (float) Math.toRadians(45.0D);
				};
				if (!billboard) {
					rotation.rotateY(yaw + spin(age));
					if (layer.variant() == FxLayer.Variant.FLYING) {
						rotation.rotateZ((float) Math.asin(Math.max(-1.0D, Math.min(1.0D, fwd.y))));
					}
					if (facing == FxLayer.Facing.FLAT) {
						rotation.rotateX((float) Math.toRadians(-90.0D));
					}
				} else if (layer.motion() == FxLayer.Motion.SPIN) {
					rotation.rotateZ(spin(age));
				}
				applyTilt(rotation, p, age);
				rotation.rotateZ(tip);
				scale = new Vector3f(s, s, s);
				offset.set(0.0F, s * 0.5F, 0.0F);
				offset.add(motionOffset(p, index));
			}
		}
		if (first) {
			key.snap(offset, rotation, scale);
		} else {
			key.key(offset, rotation, scale, 1);
		}
	}

	/** The horizontal yaw (radians, display space) of this layer: the caster's turn when riding it (S15), else the event's. */
	private float baseYaw() {
		if (attach() == FxLayer.Attach.CASTER) {
			double yawRad = Math.toRadians(ev.casterYaw());
			Vec3 look = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
			return (float) Math.atan2(-look.z, look.x);
		}
		return (float) Math.atan2(-hfwd.z, hfwd.x);
	}

	/** SWORD_RING turn per tick in radians: {@code orbit(r, deg/s)}, 0 for {@code orbit(r, 0)}, else a slow default. */
	private double ringSpeed() {
		if (layer.motion() == FxLayer.Motion.ORBIT) {
			return Math.toRadians(layer.motionB() / 20.0D);
		}
		return 0.08D;
	}

	/** 0..1 progress of the layer's motion at tick {@code age} (a pre-impact fall lands at the event's duration, S10). */
	private float motionProgress(int age) {
		float ticks = switch (layer.motion()) {
			case FALL -> preImpact && ev.duration > 0 ? ev.duration : layer.motionB();
			case RISE, GROW, SWING -> layer.motionB();
			case TOPPLE -> layer.motionA();
			case SLIDE -> ev.duration > 0 ? ev.duration : layer.motionA();
			default -> life;
		};
		return Math.min(1.0F, Math.max(0.0F, age / Math.max(1.0F, ticks)));
	}

	private float growFactor(float p) {
		if (layer.motion() != FxLayer.Motion.GROW) {
			return 1.0F;
		}
		float from = Math.max(0.05F, Math.min(1.0F, layer.motionA() / Math.max(0.01F, layer.scale() > 1.0F ? layer.scale() : 4.0F)));
		// one eased ramp (easeOutCubic): an overshoot reads as the prop growing and then shrinking
		float u = 1.0F - Math.min(1.0F, Math.max(0.0F, p));
		return from + (1.0F - from) * (1.0F - u * u * u);
	}

	private float spin(int age) {
		return layer.motion() == FxLayer.Motion.SPIN ? (float) Math.toRadians(layer.motionA() * age) : 0.0F;
	}

	private float motionLift(float p) {
		return switch (layer.motion()) {
			case FALL -> layer.motionA() * (1.0F - p * p);
			case RISE -> layer.motionA() * (1.0F - (1.0F - p) * (1.0F - p));
			default -> 0.0F;
		};
	}

	/** Fall, rise, orbit and slide as a translation from the entity position. */
	private Vector3f motionOffset(float p, int index) {
		Vector3f offset = new Vector3f(0.0F, motionLift(p), 0.0F);
		if (layer.motion() == FxLayer.Motion.SLIDE && ev.hasTarget) {
			Vec3 to = targetPoint();
			Vec3 delta = to.subtract(position(index, 0)).scale(p);
			offset.add((float) delta.x, (float) delta.y, (float) delta.z);
		} else if (layer.motion() == FxLayer.Motion.ORBIT) {
			double angle = Math.toRadians(layer.motionB() / 20.0D) * age;
			offset.add((float) (Math.cos(angle) * layer.motionA()), 0.0F, (float) (Math.sin(angle) * layer.motionA()));
		}
		return offset;
	}

	/** Topple (falls over by 90°) and swing (back and forth) about the horizontal side axis (S15: model Z after the yaw). */
	private void applyTilt(Quaternionf rotation, float p, int age) {
		if (layer.motion() == FxLayer.Motion.TOPPLE) {
			rotation.rotateZ((float) Math.toRadians(-90.0D * p * p));
		} else if (layer.motion() == FxLayer.Motion.SWING) {
			float degrees = layer.motionA() == 0.0F ? 90.0F : layer.motionA();
			float q;
			if (layer.swingPeriod() > 0) {
				// a repeating swing: back and forth once per period
				float t = ((age - swingStart) % layer.swingPeriod()) / (float) layer.swingPeriod();
				q = 0.5F - 0.5F * (float) Math.cos(t * Math.PI * 2.0D);
			} else {
				float local = Math.min(1.0F, Math.max(0.0F, (age - swingStart) / Math.max(1.0F, layer.motionB())));
				q = local * local * (3.0F - 2.0F * local);
			}
			rotation.rotateZ((float) Math.toRadians(degrees / 2.0D - degrees * q));
		}
	}
}

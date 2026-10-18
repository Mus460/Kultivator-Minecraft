package dev.kultivator.client.fx.spec;

import com.mojang.math.Transformation;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The phantom display templates of DESIGN 22.4.3, drawn with client-only Display entities from {@link PhantomDisplays}:
 * GIANT_ITEM (≤ 24), GIANT_BLOCK (≤ 24 per side), LASER (stretched block, ≤ 128 long), PETAL_RING (≤ 16 slabs opening),
 * GLYPH (billboard text, ≤ 40) and SWORD_RING (≤ 24 orbiting items, riding the caster when attached to it).
 * Keyframes are set every 2 ticks with a 2-tick interpolation: fall, rise, spin, grow, orbit, topple, slide, swing;
 * the last 6 ticks shrink to nothing. When no display can be made the layer draws its particle stand-in instead.
 */
final class DisplayRun extends LayerRun {

	private static final int STEP = 2;

	private final FxLayer.Template template;
	private final Display[] displays;
	private final boolean large;
	private boolean largeTaken;
	private final Vec3 origin;
	private final float[] scatter;
	/** The glyph text split by code point (GLYPH with n > 1 shows one per display). */
	private final int[] glyphs;
	private ParticleRun fallback;
	private boolean started;

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

	/** The item a GIANT_ITEM / SWORD_RING display shows: the layer's, or the caster's held item for {@code FxLayer.HELD}. */
	private ItemStack itemFor() {
		if (layer.item() == FxLayer.HELD) {
			net.minecraft.world.entity.Entity caster = ev.caster();
			if (caster instanceof net.minecraft.world.entity.LivingEntity living && !living.getMainHandItem().isEmpty()) {
				return living.getMainHandItem().copyWithCount(1);
			}
			return new ItemStack(Items.IRON_SWORD);
		}
		return new ItemStack(layer.item() == null ? Items.IRON_SWORD : layer.item());
	}

	DisplayRun(FxEvent ev, FxLayer layer, int life, int index) {
		super(ev, layer, life, index);
		this.template = layer.template();
		this.glyphs = layer.text().isEmpty() ? new int[0] : layer.text().codePoints().toArray();
		int wanted = switch (template) {
			case PETAL_RING -> Math.max(1, Math.min(16, layer.n() > 0 ? layer.n() : 8));
			case SWORD_RING -> Math.max(1, Math.min(24, layer.n() > 0 ? layer.n() : 8));
			case GIANT_ITEM, GIANT_BLOCK -> Math.max(1, Math.min(8, layer.n()));
			// n > 1 splits the text: one glyph per display (誅 戮 陷 絕 at the corners, the eight trigrams around a circle)
			case GLYPH -> layer.n() > 1 ? Math.max(1, Math.min(8, Math.min(layer.n(), Math.max(1, glyphs.length)))) : 1;
			default -> 1;
		};
		this.large = template == FxLayer.Template.LASER;
		this.origin = anchor();
		int granted = Math.min(wanted, ev.displaysLeft);
		ev.displaysLeft -= granted;
		this.displays = new Display[granted];
		this.scatter = new float[granted * 2];
		float r = radius(4.0F);
		boolean several = template == FxLayer.Template.GIANT_ITEM || template == FxLayer.Template.GIANT_BLOCK || template == FxLayer.Template.GLYPH;
		// With a radius, the copies stand on a ring (corners of a square field, emitters of a circle); without one, giant
		// copies stand scattered within 4 blocks and fall one after another.
		boolean ring = several && granted > 1 && layer.radius() > 0.0F;
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
		if (granted == 0) {
			fallback = new ParticleRun(ev, standIn(), life, index);
		}
	}

	/** The particle template drawn when no display may be made. */
	private FxLayer standIn() {
		FxLayer.Template stand = switch (template) {
			case GIANT_ITEM -> FxLayer.Template.SWORD_SHAPE;
			case GIANT_BLOCK -> FxLayer.Template.SHELL;
			case LASER -> FxLayer.Template.BEAM_CORE;
			case PETAL_RING -> FxLayer.Template.PETALS;
			case GLYPH -> FxLayer.Template.RUNE_CIRCLE;
			default -> FxLayer.Template.VORTEX;
		};
		int color = layer.color() == 0xFFFFFF ? 0xE8F2FF : layer.color();
		FxLayer base = FxLayer.of(stand, color).color2(layer.color2()).count(Math.max(12, layer.count())).ticks(layer.ticks())
				.attach(layer.attach()).above(layer.offsetY()).priority(layer.priority());
		if (stand == FxLayer.Template.SWORD_SHAPE) {
			base = base.size(Math.max(1.0F, Math.min(8.0F, layer.scale() / 2.0F))).variant(layer.motion() == FxLayer.Motion.FALL
					? FxLayer.Variant.FALLING : FxLayer.Variant.NONE);
		}
		if (layer.radius() > 0.0F) {
			base = base.radius(layer.radius());
		}
		if (layer.length() > 0.0F) {
			base = base.length(layer.length());
		}
		return base;
	}

	@Override
	boolean update(int age) {
		if (fallback != null) {
			return fallback.drive(age);
		}
		if (!started) {
			if (large && !largeTaken) {
				largeTaken = PhantomDisplays.takeLarge();
				if (!largeTaken) {
					fallback = new ParticleRun(ev, standIn(), life, 0);
					return true;
				}
			}
			boolean all = true;
			for (int i = 0; i < displays.length; i++) {
				if (displays[i] == null) {
					displays[i] = create(i);
					all &= displays[i] != null;
				}
			}
			if (displays.length > 0 && displays[0] == null && age > 10) {
				fallback = new ParticleRun(ev, standIn(), life, 0);
				return true;
			}
			started = all || age > 10;
		}
		boolean moving = template == FxLayer.Template.SWORD_RING || template == FxLayer.Template.LASER || layer.attach() == FxLayer.Attach.CASTER;
		for (int i = 0; i < displays.length; i++) {
			Display display = displays[i];
			if (display == null || display.isRemoved()) {
				continue;
			}
			if (moving) {
				Vec3 at = position(i, age + 1);
				display.setPos(at.x, at.y, at.z);
			}
			if (age % STEP == 0) {
				place(display, i, Math.min(life, age + STEP), age == 0 ? 0 : STEP);
			}
		}
		return true;
	}

	@Override
	void end() {
		for (Display display : displays) {
			PhantomDisplays.remove(display);
		}
		if (largeTaken) {
			PhantomDisplays.releaseLarge();
			largeTaken = false;
		}
	}

	private Display create(int index) {
		Vec3 at = position(index, 0);
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
				text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
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
		place(display, index, 0, 0);
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
			Vec3 center = layer.attach() == FxLayer.Attach.CASTER ? ev.casterPos().add(0.0D, 1.2D + layer.offsetY(), 0.0D) : origin.add(0.0D, 1.2D, 0.0D);
			float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.0F * ev.sizeF;
			double angle = Math.PI * 2.0D * index / Math.max(1, displays.length) + age * ringSpeed();
			// orbit(r, 0): banners standing still at the formation corners, no bobbing
			double bob = ringSpeed() == 0.0D ? 0.0D : Math.sin(age * 0.1D + index) * 0.2D;
			return center.add(Math.cos(angle) * r, bob, Math.sin(angle) * r);
		}
		if (template == FxLayer.Template.LASER) {
			return anchor();
		}
		if (layer.attach() == FxLayer.Attach.CASTER) {
			return ev.casterPos().add(0.0D, layer.offsetY(), 0.0D);
		}
		if (index * 2 + 1 < scatter.length) {
			return origin.add(scatter[index * 2], 0.0D, scatter[index * 2 + 1]);
		}
		return origin;
	}

	/** Sets display {@code index}'s keyframe for tick {@code age}, reached after {@code duration} ticks. */
	private void place(Display display, int index, int age, int duration) {
		float fade = Math.min(1.0F, Math.max(0.0F, (life - age) / 6.0F));
		float p = motionProgress(displays.length > 1 && template != FxLayer.Template.SWORD_RING && template != FxLayer.Template.PETAL_RING
				? Math.max(0, age - index * 3) : age);
		Vector3f offset = new Vector3f();
		Quaternionf rotation = new Quaternionf();
		Vector3f scale;
		switch (template) {
			case LASER -> {
				Vec3 start = anchor();
				Vec3 end = lineEnd(start);
				Vec3 delta = end.subtract(start);
				float length = (float) Math.min(128.0D, delta.length());
				float width = Math.max(0.1F, layer.width() > 0.0F ? layer.width() * Math.min(1.5F, ev.sizeF) : 0.6F);
				float grow = Math.min(1.0F, (age + 1) / 4.0F) * fade;
				Vec3 axis = delta.lengthSqr() < 1.0E-6D ? fwd : delta.normalize();
				rotation.rotationTo(0.0F, 0.0F, 1.0F, (float) axis.x, (float) axis.y, (float) axis.z);
				scale = new Vector3f(width * grow, width * grow, length * Math.min(1.0F, (age + 1) / 3.0F));
				offset = rotation.transform(new Vector3f(-width * grow / 2.0F, -width * grow / 2.0F, 0.0F));
			}
			case PETAL_RING -> {
				int count = Math.max(1, displays.length);
				float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.0F * ev.sizeF;
				float length = Math.min(24.0F, r);
				float width = Math.min(12.0F, length * 0.55F);
				double heading = Math.PI * 2.0D * index / count;
				float open = Math.min(1.0F, (age + 1) / 12.0F);
				float tilt = (float) Math.toRadians(85.0D - 55.0D * open);
				float yaw = (float) Math.atan2(-Math.sin(heading), Math.cos(heading));
				rotation.rotateY(yaw).rotateZ(tilt);
				scale = new Vector3f(length * fade, 0.25F, width * fade);
				offset = rotation.transform(new Vector3f(0.0F, 0.0F, -width * fade / 2.0F));
				offset.add((float) Math.cos(heading) * r * 0.15F, layer.offsetY(), (float) Math.sin(heading) * r * 0.15F);
			}
			case GLYPH -> {
				float s = Math.min(40.0F, (layer.scale() > 1.0F ? layer.scale() : 3.0F) * ev.sizeF) * fade;
				if (layer.motion() == FxLayer.Motion.GROW) {
					s *= Math.max(0.05F, layer.motionA() / Math.max(0.01F, layer.scale())) + (1.0F - Math.max(0.05F, layer.motionA()
							/ Math.max(0.01F, layer.scale()))) * p;
				}
				scale = new Vector3f(s, s, s);
				offset.set(0.0F, (layer.offsetY() > 0.0F ? 0.0F : 3.0F) + motionLift(p), 0.0F);
			}
			case SWORD_RING -> {
				float s = Math.min(24.0F, (layer.scale() > 1.0F ? layer.scale() : 1.5F) * ev.sizeF) * fade;
				double angle = Math.PI * 2.0D * index / Math.max(1, displays.length) + age * ringSpeed();
				// a still ring (orbit speed 0) shows its items upright, like planted banners
				rotation.rotateY((float) -angle).rotateZ((float) Math.toRadians(ringSpeed() == 0.0D ? 0.0D : 45.0D));
				scale = new Vector3f(s, s, s);
			}
			case GIANT_BLOCK -> {
				float mul = layer.scale() > 1.0F ? layer.scale() : 1.0F;
				float sx = Math.min(24.0F, layer.sx() * mul * ev.sizeF) * fade * growFactor(p);
				float sy = Math.min(24.0F, layer.sy() * mul * ev.sizeF) * fade * growFactor(p);
				float sz = Math.min(24.0F, layer.sz() * mul * ev.sizeF) * fade * growFactor(p);
				float yaw = (float) Math.atan2(-hfwd.z, hfwd.x) + spin(age);
				rotation.rotateY(yaw);
				applyTilt(rotation, p);
				scale = new Vector3f(sx, sy, sz);
				offset = rotation.transform(new Vector3f(-sx / 2.0F, 0.0F, -sz / 2.0F));
				offset.add(motionOffset(p));
			}
			default -> {
				float s = Math.min(24.0F, (layer.scale() > 1.0F ? layer.scale() : 4.0F) * ev.sizeF) * fade * growFactor(p);
				float yaw = (float) Math.atan2(-hfwd.z, hfwd.x) + spin(age);
				float tip = switch (layer.variant()) {
					case FALLING -> (float) Math.toRadians(-135.0D);
					case FLYING -> (float) Math.toRadians(-45.0D);
					default -> layer.motion() == FxLayer.Motion.FALL ? (float) Math.toRadians(-135.0D) : (float) Math.toRadians(45.0D);
				};
				rotation.rotateY(yaw);
				if (layer.variant() == FxLayer.Variant.FLYING) {
					rotation.rotateZ((float) Math.asin(Math.max(-1.0D, Math.min(1.0D, fwd.y))));
				}
				applyTilt(rotation, p);
				rotation.rotateZ(tip);
				scale = new Vector3f(s, s, s);
				offset.set(0.0F, s * 0.5F, 0.0F);
				offset.add(motionOffset(p));
			}
		}
		display.setTransformation(new Transformation(offset, rotation, scale, null));
		display.setTransformationInterpolationDuration(duration);
		display.setTransformationInterpolationDelay(0);
	}

	/** SWORD_RING turn per tick in radians: {@code orbit(r, deg/s)}, 0 for {@code orbit(r, 0)}, else a slow default. */
	private double ringSpeed() {
		if (layer.motion() == FxLayer.Motion.ORBIT) {
			return Math.toRadians(layer.motionB() / 20.0D);
		}
		return 0.08D;
	}

	/** 0..1 progress of the layer's motion at tick {@code age}. */
	private float motionProgress(int age) {
		float ticks = switch (layer.motion()) {
			case FALL, RISE, GROW, SWING -> layer.motionB();
			case TOPPLE, SLIDE -> layer.motionA();
			default -> life;
		};
		return Math.min(1.0F, Math.max(0.0F, age / Math.max(1.0F, ticks)));
	}

	private float growFactor(float p) {
		if (layer.motion() != FxLayer.Motion.GROW) {
			return 1.0F;
		}
		float from = Math.max(0.05F, Math.min(1.0F, layer.motionA() / Math.max(0.01F, layer.scale() > 1.0F ? layer.scale() : 4.0F)));
		return from + (1.0F - from) * p;
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
	private Vector3f motionOffset(float p) {
		Vector3f offset = new Vector3f(0.0F, motionLift(p), 0.0F);
		if (layer.motion() == FxLayer.Motion.SLIDE && ev.hasTarget) {
			Vec3 delta = ev.target.subtract(origin).scale(p);
			offset.add((float) delta.x, (float) delta.y, (float) delta.z);
		} else if (layer.motion() == FxLayer.Motion.ORBIT) {
			double angle = Math.toRadians(layer.motionB() / 20.0D) * age;
			offset.add((float) (Math.cos(angle) * layer.motionA()), 0.0F, (float) (Math.sin(angle) * layer.motionA()));
		}
		return offset;
	}

	/** Topple (falls over by 90°) and swing (back and forth) around the horizontal side axis. */
	private void applyTilt(Quaternionf rotation, float p) {
		if (layer.motion() == FxLayer.Motion.TOPPLE) {
			rotation.rotateX((float) Math.toRadians(90.0D * p * p));
		} else if (layer.motion() == FxLayer.Motion.SWING) {
			float degrees = layer.motionA() == 0.0F ? 90.0F : layer.motionA();
			rotation.rotateX((float) Math.toRadians(-degrees / 2.0D + degrees * (p * p * (3.0F - 2.0F * p))));
		}
	}
}

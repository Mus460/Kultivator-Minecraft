package dev.kultivator.technique.spec.dsl;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One visual layer of a phase (DESIGN 22.4): a particle template, a client-only phantom display, a custom renderer or a
 * post effect. Pure data that both sides know from the registry; the server only sends {@code TechFx(spec, phase, ...)}
 * and every client draws the layers of that phase with its own quality and budget. Displays are created by the client
 * only, never by the server (22.4.3).
 * <p>
 * Common fields: {@code color}/{@code color2} (RGB), {@code size}, {@code count} (particles at Normal), {@code ticks},
 * {@code minTier} (visual tier 0-5 needed), {@code priority} (CORE, DETAIL, EXTRA: what the budget drops first),
 * {@code particle} override. Display fields: {@code item}, {@code block}, {@code text}, {@code scale},
 * {@code sx}/{@code sy}/{@code sz} (block size), {@code n} and {@code radius} (rings), {@code length}/{@code width}
 * (lasers). Animation: {@code offsetY}, {@code motion} with {@code motionA}/{@code motionB} (fall height and ticks,
 * spin degrees per tick, grow from-scale and ticks, orbit radius and degrees per second...). Post: {@code intensity}
 * (shake 0-1), {@code alpha} (flash ≤ 0.35). {@code attach}: where it is drawn; {@code delay}: ticks after the event;
 * {@code onlyKehancuran}: drawn only with TechFx flag 1 (CRACKS, BLOCK_CRUMBLE).
 * <p>
 * Polish pass (DESIGN 27.3): {@code model} names a 3D prop ({@code assets/kultivator/items/fx/<model>.json}, drawn by the
 * client's ModelFx on a MODEL or GIANT_ITEM layer); {@code facing} orients displays and models; {@code fallback} is the
 * particle template drawn instead when the display cannot be (null = the client default: SHELL, or a model's particle
 * hint); {@code swingPeriod} repeats a SWING every n ticks (0 = once); {@code perTarget} draws one copy per event index or
 * HIT target; {@code atHand} anchors at the caster's main hand; {@code followUntil} keeps the layer alive until that phase
 * of the same cast arrives (or END / ABORT).
 */
public record FxLayer(Template template, int color, int color2, float size, int count, int ticks, int minTier, Priority priority,
		@Nullable ParticleOptions particle, @Nullable Item item, @Nullable BlockState block, String text, float scale, float sx,
		float sy, float sz, float offsetY, Motion motion, float motionA, float motionB, int n, float radius, float length, float width,
		@Nullable BeastKind beast, Variant variant, float intensity, float alpha, Attach attach, int delay, boolean onlyKehancuran,
		String model, Facing facing, @Nullable Template fallback, int swingPeriod, boolean perTarget, boolean atHand,
		@Nullable Phase followUntil) {

	/** The {@code item} of a GIANT_ITEM / SWORD_RING layer that shows the caster's held item instead (an iron sword when empty). */
	public static final Item HELD = net.minecraft.world.item.Items.AIR;

	/** Every template of 22.4.2-22.4.5. */
	public enum Template {
		ARC(Group.PARTICLE), CRESCENT_FLY(Group.PARTICLE), BEAM_CORE(Group.PARTICLE), CONE_SPRAY(Group.PARTICLE), SHELL(Group.PARTICLE),
		SHOCK_RING(Group.PARTICLE), DOME(Group.PARTICLE), COLUMN(Group.PARTICLE), BOLT(Group.PARTICLE), BOLT_STORM(Group.PARTICLE),
		CLOUD_DISC(Group.PARTICLE), VORTEX(Group.PARTICLE), RUNE_CIRCLE(Group.PARTICLE), PETALS(Group.PARTICLE), PALM(Group.PARTICLE),
		FIST(Group.PARTICLE), SWORD_SHAPE(Group.PARTICLE), WINGS(Group.PARTICLE), BEAST(Group.PARTICLE), TRAIL(Group.PARTICLE),
		IMPACT(Group.PARTICLE), CRACKS(Group.PARTICLE), MIST(Group.PARTICLE), SHARDS(Group.PARTICLE), CHAINS(Group.PARTICLE),
		MOTES_TO(Group.PARTICLE), HEAL_RISE(Group.PARTICLE), SHIELD_SHELL(Group.PARTICLE), AFTERIMAGE(Group.PARTICLE),
		SKY_TEAR(Group.PARTICLE), METEOR(Group.PARTICLE), STARFIELD(Group.PARTICLE),
		GIANT_ITEM(Group.DISPLAY), GIANT_BLOCK(Group.DISPLAY), LASER(Group.DISPLAY), PETAL_RING(Group.DISPLAY), GLYPH(Group.DISPLAY),
		SWORD_RING(Group.DISPLAY),
		AVATAR(Group.RENDERER), CLONE(Group.RENDERER), SWARM(Group.RENDERER), SKY_SILHOUETTE(Group.RENDERER), VIEWER_MARK(Group.RENDERER),
		SHAKE(Group.POST), FLASH(Group.POST), VIGNETTE(Group.POST), SKY(Group.POST),
		/** A 3D prop model ({@code .model(id)}): a ModelFx item display with its own animation (DESIGN 27.3). */
		MODEL(Group.DISPLAY),
		/** A segmented quad strip or chain of {@code dragon_segment} models: dragons, whips, flame and blood ribbons. */
		RIBBON(Group.RENDERER);

		private final Group group;

		Template(Group group) {
			this.group = group;
		}

		public Group group() {
			return group;
		}
	}

	public enum Group { PARTICLE, DISPLAY, RENDERER, POST }

	/** Budget priority (22.15.2): EXTRA is dropped first, then DETAIL is scaled. */
	public enum Priority { CORE, DETAIL, EXTRA }

	public enum Motion { NONE, FALL, RISE, SPIN, GROW, ORBIT, TOPPLE, SLIDE, SWING }

	/** Point-cloud beasts of BEAST and SKY_SILHOUETTE. */
	public enum BeastKind { DRAGON, BIRD, KUN, TIGER, WOLF, TORTOISE, APE, SCORPION, DEITY }

	/** Template variants (sword shapes falling / flying / fanned, mirrored rune circles, inward vortices...). */
	/** {@code NIGHT}: the layer is drawn only while it is dark outside where the viewer is (fireflies). */
	public enum Variant { NONE, FALLING, FLYING, FANNED, VERTICAL, MIRRORED, INWARD, OUTWARD, SPLIT, NIGHT }

	/**
	 * How a display or model is turned (DESIGN 27.3): FACE_VIEWER (the sprite normal points at the camera; props and item
	 * sprites), FACE_FORWARD (model -z along the caster's horizontal look; 3D models), BLADE (the old 45 degree tip, swords
	 * that fly or fall), UPRIGHT (banners, flags: no tip), FLAT (discs and seals face up).
	 */
	public enum Facing { FACE_VIEWER, FACE_FORWARD, BLADE, UPRIGHT, FLAT }

	/** Where the layer is drawn relative to the TechFx event. */
	public enum Attach {
		/** At the event position. */
		POS,
		/** At the event's second point. */
		TARGET,
		/** Riding the caster entity (auras, forms, orbiting swords). */
		CASTER,
		/** From the position to the second point (beams, trails, chains). */
		LINE,
		/** High above the event position (clouds, sky tears). */
		SKY
	}

	/** A layer of {@code template} with neutral defaults (white, size 1, 20 particles, 20 ticks, CORE, at the position). */
	public static FxLayer of(Template template, int color) {
		return new FxLayer(template, color, color, 1.0F, 20, 20, 0, Priority.CORE, null, null, null, "", 1.0F, 1.0F, 1.0F, 1.0F,
				0.0F, Motion.NONE, 0.0F, 0.0F, 0, 0.0F, 0.0F, 0.0F, null, Variant.NONE, 0.0F, 0.0F, Attach.POS, 0, false,
				"", template == Template.MODEL ? Facing.FACE_FORWARD : Facing.FACE_VIEWER, null, 0, false, false, null);
	}

	/** True when this layer shows a named 3D model (a MODEL layer, or a GIANT_ITEM with {@code .model(id)}). */
	public boolean hasModel() {
		return !model.isEmpty();
	}

	/** A copy with one field changed (every wither goes through the mutable {@link B}). */
	private FxLayer with(java.util.function.Consumer<B> edit) {
		B b = new B(this);
		edit.accept(b);
		return b.build();
	}

	/** Mutable mirror of the record for the withers. */
	private static final class B {
		Template template;
		int color;
		int color2;
		float size;
		int count;
		int ticks;
		int minTier;
		Priority priority;
		ParticleOptions particle;
		Item item;
		BlockState block;
		String text;
		float scale;
		float sx;
		float sy;
		float sz;
		float offsetY;
		Motion motion;
		float motionA;
		float motionB;
		int n;
		float radius;
		float length;
		float width;
		BeastKind beast;
		Variant variant;
		float intensity;
		float alpha;
		Attach attach;
		int delay;
		boolean onlyKehancuran;
		String model;
		Facing facing;
		Template fallback;
		int swingPeriod;
		boolean perTarget;
		boolean atHand;
		Phase followUntil;

		B(FxLayer l) {
			template = l.template;
			color = l.color;
			color2 = l.color2;
			size = l.size;
			count = l.count;
			ticks = l.ticks;
			minTier = l.minTier;
			priority = l.priority;
			particle = l.particle;
			item = l.item;
			block = l.block;
			text = l.text;
			scale = l.scale;
			sx = l.sx;
			sy = l.sy;
			sz = l.sz;
			offsetY = l.offsetY;
			motion = l.motion;
			motionA = l.motionA;
			motionB = l.motionB;
			n = l.n;
			radius = l.radius;
			length = l.length;
			width = l.width;
			beast = l.beast;
			variant = l.variant;
			intensity = l.intensity;
			alpha = l.alpha;
			attach = l.attach;
			delay = l.delay;
			onlyKehancuran = l.onlyKehancuran;
			model = l.model;
			facing = l.facing;
			fallback = l.fallback;
			swingPeriod = l.swingPeriod;
			perTarget = l.perTarget;
			atHand = l.atHand;
			followUntil = l.followUntil;
		}

		FxLayer build() {
			return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran, model, facing, fallback, swingPeriod, perTarget, atHand, followUntil);
		}
	}

	public FxLayer color2(int value) { return with(b -> b.color2 = value); }
	public FxLayer size(float value) { return with(b -> b.size = value); }
	public FxLayer count(int value) { return with(b -> b.count = value); }
	public FxLayer ticks(int value) { return with(b -> b.ticks = value); }
	public FxLayer minTier(int value) { return with(b -> b.minTier = value); }
	public FxLayer priority(Priority value) { return with(b -> b.priority = value); }
	public FxLayer detail() { return priority(Priority.DETAIL); }
	public FxLayer extra() { return priority(Priority.EXTRA); }
	public FxLayer particle(ParticleOptions value) { return with(b -> b.particle = value); }
	public FxLayer item(Item value) { return with(b -> b.item = value); }
	public FxLayer block(BlockState value) { return with(b -> b.block = value); }
	public FxLayer text(String value) { return with(b -> b.text = value); }
	/** Display scale (at most 24 for items and blocks, 40 for glyphs and models). */
	public FxLayer scale(float value) { return with(b -> b.scale = value); }
	/** Block display size x, y, z. */
	public FxLayer blockSize(float x, float y, float z) { return with(b -> { b.sx = x; b.sy = y; b.sz = z; }); }
	/** Drawn {@code blocks} above the anchor point. */
	public FxLayer above(float blocks) { return with(b -> b.offsetY = blocks); }
	private FxLayer motion(Motion value, float a, float c) { return with(b -> { b.motion = value; b.motionA = a; b.motionB = c; }); }
	/** Falls from {@code height} blocks over {@code fallTicks}. */
	public FxLayer fall(float height, float fallTicks) { return motion(Motion.FALL, height, fallTicks); }
	/** Rises {@code height} blocks over {@code riseTicks}. */
	public FxLayer rise(float height, float riseTicks) { return motion(Motion.RISE, height, riseTicks); }
	public FxLayer spin(float degreesPerTick) { return motion(Motion.SPIN, degreesPerTick, 0.0F); }
	/** Grows from {@code fromScale} to the display scale over {@code growTicks}. */
	public FxLayer grow(float fromScale, float growTicks) { return motion(Motion.GROW, fromScale, growTicks); }
	public FxLayer orbit(float orbitRadius, float degreesPerSecond) { return motion(Motion.ORBIT, orbitRadius, degreesPerSecond); }
	/** Topples along the line over {@code toppleTicks}. */
	public FxLayer topple(float toppleTicks) { return motion(Motion.TOPPLE, toppleTicks, 0.0F); }
	/** Slides from the position to the target point over {@code slideTicks}. */
	public FxLayer slide(float slideTicks) { return motion(Motion.SLIDE, slideTicks, 0.0F); }
	/** Swings {@code degrees} about the side axis over {@code swingTicks}. */
	public FxLayer swing(float degrees, float swingTicks) { return motion(Motion.SWING, degrees, swingTicks); }
	public FxLayer n(int value) { return with(b -> b.n = value); }
	public FxLayer radius(float value) { return with(b -> b.radius = value); }
	public FxLayer length(float value) { return with(b -> b.length = value); }
	public FxLayer width(float value) { return with(b -> b.width = value); }
	public FxLayer beast(BeastKind value) { return with(b -> b.beast = value); }
	public FxLayer variant(Variant value) { return with(b -> b.variant = value); }
	public FxLayer falling() { return variant(Variant.FALLING); }
	public FxLayer flying() { return variant(Variant.FLYING); }
	public FxLayer fanned() { return variant(Variant.FANNED); }
	public FxLayer mirrored() { return variant(Variant.MIRRORED); }
	public FxLayer inward() { return variant(Variant.INWARD); }
	public FxLayer vertical() { return variant(Variant.VERTICAL); }
	public FxLayer split() { return variant(Variant.SPLIT); }
	public FxLayer intensity(float value) { return with(b -> b.intensity = value); }
	public FxLayer alpha(float value) { return with(b -> b.alpha = value); }
	public FxLayer attach(Attach value) { return with(b -> b.attach = value); }
	public FxLayer onCaster() { return attach(Attach.CASTER); }
	public FxLayer onTarget() { return attach(Attach.TARGET); }
	public FxLayer line() { return attach(Attach.LINE); }
	public FxLayer inSky() { return attach(Attach.SKY); }
	public FxLayer delay(int value) { return with(b -> b.delay = value); }
	public FxLayer kehancuranOnly() { return with(b -> b.onlyKehancuran = true); }

	// ------------------------------------------------------------------ polish pass (DESIGN 27.3)

	/**
	 * The 3D prop {@code id} ({@code kultivator:fx/<id>}). On a GIANT_ITEM it replaces the item; on any layer the client
	 * draws the model when it can and the {@link #fallback} otherwise. A model faces forward unless told otherwise.
	 */
	public FxLayer model(String id) {
		return with(b -> {
			b.model = id == null ? "" : id;
			if (b.facing == Facing.FACE_VIEWER) {
				b.facing = Facing.FACE_FORWARD;
			}
		});
	}
	public FxLayer facing(Facing value) { return with(b -> b.facing = value); }
	/** The particle template drawn instead of the display or model (Minimal quality, budget refused, tier far below). */
	public FxLayer fallback(Template value) { return with(b -> b.fallback = value); }
	/** Repeats the SWING every {@code ticks} (also on every IMPACT event of the same cast). */
	public FxLayer swingEvery(int ticks) { return with(b -> b.swingPeriod = Math.max(0, ticks)); }
	/** One copy per event index (multi-point groups) or per HIT target (read as {@link #perTarget()}). */
	public FxLayer eachTarget() { return with(b -> b.perTarget = true); }
	/** Anchored at the caster's main hand (props held up, a sword growing in the hand; read as {@link #atHand()}). */
	public FxLayer fromHand() { return with(b -> { b.atHand = true; b.attach = Attach.CASTER; }); }
	/** Lives until {@code phase} of the same cast arrives (or END / ABORT), whatever its ticks say. */
	public FxLayer until(Phase phase) { return with(b -> b.followUntil = phase); }
}

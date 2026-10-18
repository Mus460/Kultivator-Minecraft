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
 */
public record FxLayer(Template template, int color, int color2, float size, int count, int ticks, int minTier, Priority priority,
		@Nullable ParticleOptions particle, @Nullable Item item, @Nullable BlockState block, String text, float scale, float sx,
		float sy, float sz, float offsetY, Motion motion, float motionA, float motionB, int n, float radius, float length,
		float width, @Nullable BeastKind beast, Variant variant, float intensity, float alpha, Attach attach, int delay,
		boolean onlyKehancuran) {

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
		SHAKE(Group.POST), FLASH(Group.POST), VIGNETTE(Group.POST), SKY(Group.POST);

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
				0.0F, Motion.NONE, 0.0F, 0.0F, 0, 0.0F, 0.0F, 0.0F, null, Variant.NONE, 0.0F, 0.0F, Attach.POS, 0, false);
	}

	public FxLayer color2(int value) { return new FxLayer(template, color, value, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer size(float value) { return new FxLayer(template, color, color2, value, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer count(int value) { return new FxLayer(template, color, color2, size, value, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer ticks(int value) { return new FxLayer(template, color, color2, size, count, value, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer minTier(int value) { return new FxLayer(template, color, color2, size, count, ticks, value, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer priority(Priority value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, value, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer detail() { return priority(Priority.DETAIL); }
	public FxLayer extra() { return priority(Priority.EXTRA); }
	public FxLayer particle(ParticleOptions value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, value, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer item(Item value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, value, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer block(BlockState value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, value, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer text(String value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, value, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	/** Display scale (≤ 24 for items and blocks, ≤ 40 for glyphs). */
	public FxLayer scale(float value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, value, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	/** Block display size x × y × z. */
	public FxLayer blockSize(float x, float y, float z) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, x, y, z, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	/** Drawn {@code blocks} above the anchor point. */
	public FxLayer above(float blocks) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, blocks, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	private FxLayer motion(Motion value, float a, float b) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, value, a, b, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
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
	public FxLayer swing(float degrees, float swingTicks) { return motion(Motion.SWING, degrees, swingTicks); }
	public FxLayer n(int value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, value, radius, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer radius(float value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, value, length, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer length(float value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, value, width, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer width(float value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, value, beast, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer beast(BeastKind value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, value, variant, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer variant(Variant value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, value, intensity, alpha, attach, delay, onlyKehancuran); }
	public FxLayer falling() { return variant(Variant.FALLING); }
	public FxLayer flying() { return variant(Variant.FLYING); }
	public FxLayer fanned() { return variant(Variant.FANNED); }
	public FxLayer mirrored() { return variant(Variant.MIRRORED); }
	public FxLayer inward() { return variant(Variant.INWARD); }
	public FxLayer intensity(float value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, value, alpha, attach, delay, onlyKehancuran); }
	public FxLayer alpha(float value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, value, attach, delay, onlyKehancuran); }
	public FxLayer attach(Attach value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, value, delay, onlyKehancuran); }
	public FxLayer onCaster() { return attach(Attach.CASTER); }
	public FxLayer onTarget() { return attach(Attach.TARGET); }
	public FxLayer line() { return attach(Attach.LINE); }
	public FxLayer inSky() { return attach(Attach.SKY); }
	public FxLayer delay(int value) { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, value, onlyKehancuran); }
	public FxLayer kehancuranOnly() { return new FxLayer(template, color, color2, size, count, ticks, minTier, priority, particle, item, block, text, scale, sx, sy, sz, offsetY, motion, motionA, motionB, n, radius, length, width, beast, variant, intensity, alpha, attach, delay, true); }
}

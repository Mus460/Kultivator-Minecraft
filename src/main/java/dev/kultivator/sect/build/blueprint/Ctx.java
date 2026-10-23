package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.Map;
import java.util.Random;

/**
 * The immutable inputs of one emission (DESIGN 21.9): the template spec, the site rotation (0-3, gate south/west/north/
 * east), the glaze, the plan seed, the frozen terrain (template-local) and the fitted zone levels. Ops are a pure function
 * of these, so a component re-emits identically after a restart (idempotent resume). Safe to share between threads.
 */
public final class Ctx {

	private final TemplateSpec spec;
	private final int rotation;
	private final Glaze glaze;
	private final long seed;
	private final TerrainView terrain;
	private final Map<String, Integer> levels;

	public Ctx(TemplateSpec spec, int rotation, Glaze glaze, long seed, TerrainView terrain, Map<String, Integer> levels) {
		this.spec = spec;
		this.rotation = Math.floorMod(rotation, 4);
		this.glaze = glaze == null ? Glaze.TINTA : glaze;
		this.seed = seed;
		this.terrain = terrain == null ? TerrainView.flat(-1) : terrain;
		this.levels = levels == null ? Map.of() : Map.copyOf(levels);
	}

	/**
	 * A dry-run context: flat land at the plaza floor, rotation 0, every zone at its design level. A sky template stands on
	 * its own islands over flat land at its stair foot's design level (DESIGN 37.4.9).
	 */
	public static Ctx dry(TemplateSpec spec, Glaze glaze) {
		if (spec.sky()) {
			TemplateSpec.Zone kaki = spec.kaki();
			int ground = kaki == null ? -150 : kaki.design() - 1;
			return new Ctx(spec, 0, glaze, 0x5EC7L, new dev.kultivator.sect.build.blueprint.awan.AwanTerrain(spec.template(), TerrainView.flat(ground)),
					Map.of());
		}
		return new Ctx(spec, 0, glaze, 0x5EC7L, TerrainView.flat(-1), Map.of());
	}

	public TemplateSpec spec() {
		return spec;
	}

	public int template() {
		return spec.template();
	}

	public int rotation() {
		return rotation;
	}

	public Glaze glaze() {
		return glaze;
	}

	public long seed() {
		return seed;
	}

	public TerrainView terrain() {
		return terrain;
	}

	public Map<String, Integer> levels() {
		return levels;
	}

	/** The standing level (template-local y people stand at) of {@code lv}: fixed, or the zone's fitted level + offset. */
	public int level(Lv lv) {
		if (lv == null) {
			return 0;
		}
		if (lv.fixed()) {
			return lv.offset();
		}
		Integer fitted = levels.get(lv.zone());
		if (fitted != null) {
			return fitted + lv.offset();
		}
		TemplateSpec.Zone zone = spec.zone(lv.zone());
		return (zone == null ? 0 : zone.design()) + lv.offset();
	}

	/** A deterministic random for one part ({@code salt} = a stable name such as the component id). */
	public Random random(String salt) {
		return new Random(seed * 0x9E3779B97F4A7C15L ^ (salt == null ? 0 : salt.hashCode()) * 0xC2B2AE3D27D4EB4FL);
	}

	/** The roof materials of {@code rank} in this plan's glaze. */
	public RoofMat roof(Rank rank) {
		return rank.roof(glaze);
	}

	/**
	 * The template-local direction that points to the world compass direction {@code world} once the site rotation is
	 * applied: trigrams and plaques are laid out in true compass orientation whatever the rotation (DESIGN 21.5, 21.9).
	 */
	public Direction compass(Direction world) {
		if (world.getAxis().isVertical()) {
			return world;
		}
		Rotation undo = switch (rotation) {
			case 1 -> Rotation.COUNTERCLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.CLOCKWISE_90;
			default -> Rotation.NONE;
		};
		return undo.rotate(world);
	}
}

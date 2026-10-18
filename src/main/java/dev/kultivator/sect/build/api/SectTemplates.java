package dev.kultivator.sect.build.api;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

/**
 * The shared numbering of DESIGN 21.1 and 21.2 that both 6b modules use: template numbers 1-9 with their stable ids,
 * tiers 1-9, and the rotation convention. Names live in the blueprint's language fragment ({@code lang-src/blueprint.*})
 * under {@link #nameKey(int)} and {@link #tierKey(int)}.
 * <p>
 * Rotation {@code rot} 0-3 = the number of clockwise quarter turns applied to template-local coordinates
 * ({@link Rotation#NONE}, {@link Rotation#CLOCKWISE_90}, {@link Rotation#CLOCKWISE_180},
 * {@link Rotation#COUNTERCLOCKWISE_90}). Template-local +z points to the gate, so rot 0 has the gate facing south, 1 west,
 * 2 north and 3 east (vanilla {@code BlockPos.rotate}: a clockwise turn maps (x, z) to (-z, x)).
 */
public final class SectTemplates {

	public static final int COUNT = 9;
	public static final int TIERS = 9;
	/** Stable ids of templates 1-9 (Table A); index = template - 1. Never change them (saved, used in keys). */
	public static final String[] IDS = {"unranked", "third_rate", "second_rate", "first_rate", "great", "holy_land", "cave_heaven",
			"ancient_lineage", "heavenly_court"};

	public static boolean valid(int template) {
		return template >= 1 && template <= COUNT;
	}

	/** The id of template 1-9, or "none". */
	public static String id(int template) {
		return valid(template) ? IDS[template - 1] : "none";
	}

	/** Template 1-9 of an id, 0 when unknown. */
	public static int byId(String id) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) {
				return i + 1;
			}
		}
		return 0;
	}

	/** {@code kultivator.blueprint.template.<id>} (defined by the blueprint module). */
	public static String nameKey(int template) {
		return "kultivator.blueprint.template." + id(template);
	}

	public static Component name(int template) {
		return Component.translatable(nameKey(template));
	}

	/** {@code kultivator.blueprint.tier.<1-9>} (defined by the blueprint module). */
	public static String tierKey(int tier) {
		return "kultivator.blueprint.tier." + Math.max(1, Math.min(TIERS, tier));
	}

	public static Component tierName(int tier) {
		return Component.translatable(tierKey(tier));
	}

	/** The vanilla rotation of {@code rot} (taken modulo 4). */
	public static Rotation rotation(int rot) {
		return switch (Math.floorMod(rot, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** The direction the gate faces with {@code rot}. */
	public static Direction gate(int rot) {
		return switch (Math.floorMod(rot, 4)) {
			case 1 -> Direction.WEST;
			case 2 -> Direction.NORTH;
			case 3 -> Direction.EAST;
			default -> Direction.SOUTH;
		};
	}

	/** The {@code rot} whose gate faces {@code gate} (a vertical direction gives 0). */
	public static int rotFor(Direction gate) {
		return switch (gate) {
			case WEST -> 1;
			case NORTH -> 2;
			case EAST -> 3;
			default -> 0;
		};
	}

	private SectTemplates() {
	}
}

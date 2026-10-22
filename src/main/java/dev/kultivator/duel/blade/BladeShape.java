package dev.kultivator.duel.blade;

import dev.kultivator.duel.clash.WeaponClass;
import org.joml.Vector3f;

/**
 * Where the blade is on the held item (DESIGN 35.5.3): the base of the blade ({@code grip}) and its point ({@code tip})
 * in item space centred on the item ([-0.5, 0.5]³, i.e. model pixels / 16 − 0.5, x right and y up on the item texture,
 * z through the flat sprite), plus the width of the edge and, for the mace, the radius of its head.
 * <p>
 * The vanilla sword sprite runs from the guard at pixel (5, 11) to the point at pixel (15, 1): (−0.19, −0.19, 0) to
 * (0.44, 0.44, 0). The other classes follow the vanilla sprites of their kind (the axe from the upper haft to the top of
 * its head, the spear and trident 1.6 times longer, the mace's round head). {@link #SWORD_3D} is the disciple's 3D
 * natal and qi sword ({@code models/item/fx/light_sword}, {@code natal_sword}: blade from model y 8 to 31 px along +y),
 * expressed in the frame of {@code DiscipleHeldItemLayer.swordTransform} after the 180° roll of {@code FormModels}.
 * The fist ({@code TANGAN_KOSONG}) is a short segment in the arm's own frame (model pixels / 16): the knuckles.
 */
public record BladeShape(Vector3f grip, Vector3f tip, float edgeWidth, float headRadius, Hold hold) {

	/** Which transform chain carries the shape from the hand to the item. */
	public enum Hold {
		/** {@code ItemInHandLayer} + the {@code handheld} third-person display (every vanilla-like item). */
		ITEM,
		/** {@code DiscipleHeldItemLayer}'s 3D sword (no display transform, the model's own geometry). */
		SWORD_3D,
		/** No item: the shape is in the arm's frame. */
		ARM
	}

	public static final BladeShape PEDANG = new BladeShape(v(-0.19F, -0.19F, 0.0F), v(0.44F, 0.44F, 0.0F), 0.09F, 0.0F, Hold.ITEM);
	/** Great swords: the same guard, the blade 1.25 times longer. */
	public static final BladeShape PEDANG_BERAT = new BladeShape(v(-0.19F, -0.19F, 0.0F), along(-0.19F, 0.44F, 1.25F), 0.12F, 0.0F, Hold.ITEM);
	/** Axes: the upper haft (pixel 6, 10) to the top of the head (pixel 13, 2); the edge is the wide head. */
	public static final BladeShape KAPAK = new BladeShape(v(-0.125F, -0.125F, 0.0F), v(0.3125F, 0.375F, 0.0F), 0.22F, 0.0F, Hold.ITEM);
	/** Spears and the trident: the shaft from the hand to the point, 1.6 times the sword's reach. */
	public static final BladeShape TOMBAK = new BladeShape(v(-0.19F, -0.19F, 0.0F), along(-0.19F, 0.5F, 1.6F), 0.06F, 0.0F, Hold.ITEM);
	/** The mace: the haft up to a round head of radius 0.18 at the tip. */
	public static final BladeShape GADA = new BladeShape(v(-0.10F, -0.10F, 0.0F), v(0.30F, 0.30F, 0.0F), 0.0F, 0.18F, Hold.ITEM);
	/** The bare fist, in the right arm's frame (left arms mirror x). */
	public static final BladeShape FIST = new BladeShape(v(-1.0F / 16.0F, 8.0F / 16.0F, -0.5F / 16.0F), v(-1.0F / 16.0F, 12.0F / 16.0F,
			-0.5F / 16.0F), 0.25F, 0.0F, Hold.ARM);
	/** The disciples' 3D sword (natal sword, qi sword). */
	public static final BladeShape SWORD_3D = new BladeShape(v(0.0F, 8.0F / 16.0F - 0.5F, 0.0F), v(0.0F, 31.0F / 16.0F - 0.5F, 0.0F), 0.12F,
			0.0F, Hold.SWORD_3D);

	/** The shape of a weapon class. */
	public static BladeShape of(WeaponClass weapon) {
		if (weapon == null) {
			return FIST;
		}
		return switch (weapon) {
			case PEDANG -> PEDANG;
			case PEDANG_BERAT -> PEDANG_BERAT;
			case KAPAK -> KAPAK;
			case TOMBAK -> TOMBAK;
			case GADA -> GADA;
			default -> FIST;
		};
	}

	/** Blade length in item units (before the item and model scales). */
	public float length() {
		return grip.distance(tip);
	}

	private static Vector3f v(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	/** A point on the sprite's diagonal: from {@code base} (x = y) towards {@code end} (x = y), {@code factor} times as far. */
	private static Vector3f along(float base, float end, float factor) {
		float p = base + (end - base) * factor;
		return new Vector3f(p, p, 0.0F);
	}
}

package dev.kultivator.client.aura;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.disciple.power.PowerIds;

import java.util.List;

/**
 * The colours of one aura (DESIGN 31.3): the realm's own body and core colour (the 31.4 tables, not {@link Realm#color()},
 * whose white is too flat for Immortal Ascension), the Dao tint, the root's inflow colour, the root's element colours, the
 * accent of a Heavenly root or a disciple's special physique and the amber of a full bottleneck.
 */
public final class AuraColors {

	/** Body colours of the nine realms (31.4). */
	private static final int[] BODY = {0xF2E8D8, 0xDDF3E6, 0x7FE0FF, 0xFFD54A, 0xB98CFF, 0xFF7AD9, 0x7CFFB2, 0xFFF4D0, 0xFFE9A8};
	/** Core (hot centre) colours of the nine realms. */
	private static final int[] CORE = {0xFFF8EE, 0xFFFFFF, 0xE8FBFF, 0xFFF6C8, 0xE9DCFF, 0xFFF0FA, 0xF6FFF9, 0xFFFFFF, 0xFFFFFF};
	/** Accent of a Heavenly root ({@code RootGrade.HEAVENLY.color()}). */
	public static final int HEAVENLY = 0xFFC93C;
	/** The breakthrough-ready amber and its hot red end. */
	public static final int WARN = 0xFF9A3A;
	public static final int WARN_HOT = 0xFF4A3A;
	/** Physique accents by {@code PowerIds.PHYSIQUE_*} (index 0: none). */
	private static final int[] PHYSIQUE = {-1, 0x6BCB3A, 0x7FD8FF, 0xC02030, 0xFFD54A, 0x5A2A8A, 0xF0F0FF, 0x9A6AFF, 0xD04020, 0xD8D8D8};

	/**
	 * The palette of a subject: {@code body}/{@code core} of the realm, {@code tint} (the Dao colour, else body),
	 * {@code inflow} (the root's main colour, else body), {@code elements} (≤ 4 root element colours; the tint when the
	 * mask is empty), {@code accent} (−1 for none) and whether that accent is a Heavenly root's.
	 */
	public record Palette(int body, int core, int tint, int inflow, int[] elements, int accent, boolean heavenly) {

		/** Ribbons, rings and decals: 35 % of the tint. */
		public int ring() {
			return mix(body, tint, 0.35F);
		}

		/** Glows: 20 % of the tint. */
		public int glow() {
			return mix(body, tint, 0.2F);
		}

		/** The body mixed {@code t} toward the tint. */
		public int tinted(float t) {
			return mix(body, tint, t);
		}

		public boolean hasAccent() {
			return accent != -1;
		}

		/** The amber warning colour at pulse {@code p} (0..1). */
		public int warn(float p) {
			return mix(WARN, WARN_HOT, p);
		}
	}

	public static Palette of(AuraSubject s) {
		int realm = s.realm().ordinal();
		int body = BODY[Math.max(0, Math.min(BODY.length - 1, realm))];
		int core = CORE[Math.max(0, Math.min(CORE.length - 1, realm))];
		Dao dao = Dao.byOrdinal(s.dao());
		int tint = dao != null ? dao.color() & 0xFFFFFF : body;
		int inflow = s.rootElements() != 0 && s.rootColor() != 0 ? s.rootColor() & 0xFFFFFF : body;
		List<Element> list = Element.fromMask(s.rootElements());
		int count = Math.min(4, list.size());
		int[] elements;
		if (count == 0) {
			elements = new int[]{tint};
		} else {
			elements = new int[count];
			for (int i = 0; i < count; i++) {
				elements[i] = list.get(i).color() & 0xFFFFFF;
			}
		}
		return new Palette(body, core, tint, inflow, elements, s.accent(), s.accent() == HEAVENLY);
	}

	/** The realm body colour (31.4). */
	public static int body(Realm realm) {
		return BODY[Math.max(0, Math.min(BODY.length - 1, realm.ordinal()))];
	}

	/** The accent of a disciple's physique ({@code PowerIds.PHYSIQUE_*}), −1 for none or an unknown id. */
	public static int physique(int physique) {
		return physique > 0 && physique < PHYSIQUE.length && physique <= PowerIds.PHYSIQUE_ATTRIBUTELESS ? PHYSIQUE[physique] : -1;
	}

	/** The Heavenly root accent (kept equal to {@code RootGrade.HEAVENLY.color()}). */
	public static int heavenly() {
		return RootGrade.HEAVENLY.color() & 0xFFFFFF;
	}

	public static int mix(int a, int b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
	}

	/** {@code rgb} lifted {@code t} toward white. */
	public static int lighten(int rgb, float t) {
		return mix(rgb, 0xFFFFFF, t);
	}

	/** {@code rgb} darkened by {@code t}. */
	public static int darken(int rgb, float t) {
		return mix(rgb, 0x000000, t);
	}

	private static int clamp(int c) {
		return Math.max(0, Math.min(255, c));
	}

	private AuraColors() {
	}
}

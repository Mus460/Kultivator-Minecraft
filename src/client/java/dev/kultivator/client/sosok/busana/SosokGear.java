package dev.kultivator.client.sosok.busana;

import dev.kultivator.client.dunia.dimensi.render.Headwear;
import dev.kultivator.client.dunia.kota.KotaGear;

import java.util.HashMap;
import java.util.Map;

/**
 * Old head gear on Sosok bodies (DESIGN 40.4.5, contract C40.K2; module 40K). Two tables:
 * <ul>
 *   <li>{@link #SEMBUNYI}: the named parts of a {@code KotaGear} outfit that a Sosok body hides, because Sosok now draws
 *   that hair, beard or shawl itself in the body's colours ({@code KotaGearLayer}, 40R, reads it once per renderer);</li>
 *   <li>{@link #TOPI_MENUTUP}: the gear kinds that cover the head. Under them the resolver hides the hair volume (buns,
 *   guan, pins, buyao, flowers, kids' tufts) and paints the flat "bertopi" hair sheet (40.3.6); a ponytail stays.</li>
 * </ul>
 * {@code Headwear} (Dimension D) has its own pair of tables. DESIGN 38 gear (38MD {@code KotaGear} kinds 21-60, 38TV
 * theme gear) registers itself with {@link #daftarTopi} / {@link #daftarHeadwear} from its own client init. The
 * constants of {@code KotaGear} and {@code Headwear} are only read here, never changed.
 */
public final class SosokGear {

	/** KotaGear kind -> part names hidden on a Sosok body. */
	public static final Map<Integer, String[]> SEMBUNYI = new HashMap<>();
	/** KotaGear kind -> covers the head. */
	public static final Map<Integer, Boolean> TOPI_MENUTUP = new HashMap<>();
	/** Headwear kind -> part names hidden on a Sosok body. */
	public static final Map<Integer, String[]> SEMBUNYI_HEADWEAR = new HashMap<>();
	/** Headwear kind -> covers the head. */
	public static final Map<Integer, Boolean> HEADWEAR_MENUTUP = new HashMap<>();

	static {
		// 40.4.5: the old outfits that carried hair
		sembunyi(KotaGear.TEA, "bun", "pin", "pin_bead", "hairband");
		sembunyi(KotaGear.FOLK_BUN, "bun", "pin");
		sembunyi(KotaGear.FOLK_KNOT, "knot", "wrap");
		sembunyi(KotaGear.WANDERER_GUAN, "knot", "guan", "pin", "ribbon_l", "ribbon_r");
		sembunyi(KotaGear.RIVAL, "knot", "guan", "gem");
		sembunyi(KotaGear.RIVAL_LEADER, "knot", "guan", "gem");
		sembunyi(KotaGear.IMMORTAL, "guan", "pin", "hair", "shawl", "ribbon");
		sembunyi(KotaGear.SAGE, "beard", "brows");
		// the covering hats of the old outfits
		for (int kind : new int[] {KotaGear.FUTOU, KotaGear.FUTOU_GOLD, KotaGear.BROKER, KotaGear.MERCHANT, KotaGear.MERCHANT_GOLD,
				KotaGear.GUARD, KotaGear.FOLK_HAT, KotaGear.FOLK_SCARF, KotaGear.WANDERER_DOULI, KotaGear.SAGE, KotaGear.ENVOY}) {
			TOPI_MENUTUP.put(kind, Boolean.TRUE);
		}
		// Dimension D: the ghost hair and eyes are Sosok's own on a d.arwah body; these hats cover the head
		SEMBUNYI_HEADWEAR.put(Headwear.GHOST_HAIR, new String[] {"*"});
		SEMBUNYI_HEADWEAR.put(Headwear.GHOST_EYES, new String[] {"*"});
		for (int kind : new int[] {Headwear.JUDGE, Headwear.YAMA, Headwear.GUARD, Headwear.MERCHANT, Headwear.FISHER, Headwear.PENGLAI,
				Headwear.HERMIT}) {
			HEADWEAR_MENUTUP.put(kind, Boolean.TRUE);
		}
	}

	private static void sembunyi(int kind, String... parts) {
		SEMBUNYI.put(kind, parts);
	}

	/**
	 * Registers a {@code KotaGear} kind (38MD, 38TV): whether it covers the head, and the part names a Sosok body hides
	 * (hair pieces of that outfit; usually none, the 38 outfits carry no hair). Call from client init, before the entity
	 * renderers are made. A later call for the same kind replaces the earlier one.
	 */
	public static void daftarTopi(int kotaGearKind, boolean menutup, String... bagianDisembunyikan) {
		if (menutup) {
			TOPI_MENUTUP.put(kotaGearKind, Boolean.TRUE);
		} else {
			TOPI_MENUTUP.remove(kotaGearKind);
		}
		if (bagianDisembunyikan.length > 0) {
			SEMBUNYI.put(kotaGearKind, bagianDisembunyikan.clone());
		}
	}

	/** The same for a Dimension D {@code Headwear} kind ({@code "*"} = the whole headwear is not drawn on Sosok). */
	public static void daftarHeadwear(int kind, boolean menutup, String... bagianDisembunyikan) {
		if (menutup) {
			HEADWEAR_MENUTUP.put(kind, Boolean.TRUE);
		} else {
			HEADWEAR_MENUTUP.remove(kind);
		}
		if (bagianDisembunyikan.length > 0) {
			SEMBUNYI_HEADWEAR.put(kind, bagianDisembunyikan.clone());
		}
	}

	/** True when this KotaGear or Headwear kind covers the head (0 = none). */
	public static boolean menutup(int kotaGearKind, int headwearKind) {
		return kotaGearKind > 0 && TOPI_MENUTUP.containsKey(kotaGearKind) || headwearKind > 0 && HEADWEAR_MENUTUP.containsKey(headwearKind);
	}

	/** Parts of Headwear kind {@code kind} hidden on Sosok bodies; never null. */
	public static String[] sembunyiHeadwear(int kind) {
		String[] s = SEMBUNYI_HEADWEAR.get(kind);
		return s == null ? new String[0] : s;
	}

	private SosokGear() {
	}
}

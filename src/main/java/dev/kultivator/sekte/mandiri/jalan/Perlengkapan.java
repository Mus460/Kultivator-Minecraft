package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * The disciple's own gear (DESIGN 39.6.3, 39.6.7), {@code extra.mandiri.perlengkapan}: a sword tier 0-5 and a robe tier
 * 0-3 bought in town. The sword adds {@code x(1 + 0.04 x tier)} to melee damage (at most x1.2, techniques untouched)
 * through {@code DiscipleHooks.addDamageFactor}; a new robe honours the disciple once a day for five days
 * ({@code SekteBus.honored}). Shop prices of the next tier come from the 38.M sword and robe rows: swords 15-20 / 60-80 /
 * 240-300 / 900-1100 NB, robes 20 / 60 / 200 NB; tier 5 swords only come from a smith's order.
 * <p>
 * In the shopping list a tier is a pseudo item {@code kultivator:pedang_tier_<n>} / {@code kultivator:jubah_tier_<n>}:
 * it never becomes a stack, the tier is set when the goods are unpacked.
 */
public final class Perlengkapan {

	public static final String PEDANG = "pedang";
	public static final String JUBAH = "jubah";
	public static final String JUBAH_HARI = "jubahHari";
	/** Shop price range of sword tiers 1-4 (NB). */
	static final long[][] HARGA_PEDANG = {{0L, 0L}, {15L, 20L}, {60L, 80L}, {240L, 300L}, {900L, 1100L}};
	/** Shop price of robe tiers 1-3 (NB). */
	static final long[] HARGA_JUBAH = {0L, 20L, 60L, 200L};
	public static final int PEDANG_TOKO_MAKS = 4;
	public static final int JUBAH_MAKS = 3;

	private static boolean hooked;

	static void init() {
		if (hooked) {
			return;
		}
		hooked = true;
		DiscipleHooks.addDamageFactor((entity, target, technique) -> technique ? 1.0D : faktor(entity));
	}

	/** The sword's melee factor of a disciple. */
	static double faktor(DiscipleEntity entity) {
		if (entity == null || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return 1.0D;
		}
		int tier = pedang(entity.data());
		return Math.min(1.2D, 1.0D + 0.04D * tier);
	}

	public static int pedang(DiscipleData data) {
		return Math.max(0, Math.min(5, MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_PERLENGKAPAN).getIntOr(PEDANG, 0)));
	}

	public static int jubah(DiscipleData data) {
		return Math.max(0, Math.min(JUBAH_MAKS, MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_PERLENGKAPAN).getIntOr(JUBAH, 0)));
	}

	/** The pseudo item id of the next sword tier, or null at the shop's top. */
	static String pedangBerikut(DiscipleData data) {
		int next = pedang(data) + 1;
		return next > PEDANG_TOKO_MAKS ? null : "kultivator:pedang_tier_" + next;
	}

	static String jubahBerikut(DiscipleData data) {
		int next = jubah(data) + 1;
		return next > JUBAH_MAKS ? null : "kultivator:jubah_tier_" + next;
	}

	/** True for a gear pseudo item. */
	static boolean semu(String itemId) {
		return itemId != null && (itemId.startsWith("kultivator:pedang_tier_") || itemId.startsWith("kultivator:jubah_tier_"));
	}

	/** The tier of a gear pseudo item (0 when none). */
	static int tierDari(String itemId) {
		int us = itemId == null ? -1 : itemId.lastIndexOf('_');
		if (us < 0) {
			return 0;
		}
		try {
			return Integer.parseInt(itemId.substring(us + 1));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The base NB of a gear pseudo item in a town on a day (the range drawn by the town-day seed). */
	static long harga(String itemId, long benih) {
		int tier = tierDari(itemId);
		if (itemId.startsWith("kultivator:pedang_tier_")) {
			if (tier < 1 || tier > PEDANG_TOKO_MAKS) {
				return -1L;
			}
			long lo = HARGA_PEDANG[tier][0];
			long hi = HARGA_PEDANG[tier][1];
			return lo + Math.floorMod(benih, hi - lo + 1L);
		}
		return tier < 1 || tier > JUBAH_MAKS ? -1L : HARGA_JUBAH[tier];
	}

	/** Applies a bought gear tier to the data (unpacking at arrival); true when it raised a tier. */
	static boolean pasang(DiscipleData data, String itemId, long hari) {
		int tier = tierDari(itemId);
		CompoundTag section = MandiriApi.bagianTulis(data);
		CompoundTag gear = section.getCompound(MandiriIds.K_PERLENGKAPAN).orElse(null);
		if (gear == null) {
			gear = new CompoundTag();
			section.put(MandiriIds.K_PERLENGKAPAN, gear);
		}
		String key = itemId.startsWith("kultivator:pedang_tier_") ? PEDANG : JUBAH;
		if (tier <= gear.getIntOr(key, 0)) {
			return false;
		}
		gear.putInt(key, tier);
		if (JUBAH.equals(key)) {
			gear.putLong(JUBAH_HARI, hari);
		}
		return true;
	}

	/** A new robe's pride: honoured +1 once a day for five days (from the daily step of a loaded disciple). */
	static void hariBaru(DiscipleEntity murid, long hari) {
		CompoundTag gear = MandiriApi.bagian(murid.data()).getCompoundOrEmpty(MandiriIds.K_PERLENGKAPAN);
		long since = gear.getLongOr(JUBAH_HARI, -100L);
		if (gear.getIntOr(JUBAH, 0) > 0 && hari > since && hari <= since + 5L && murid.level() instanceof ServerLevel level) {
			SekteBus.honored(level.getServer(), murid.ownerId(), murid.soulId(), "mandiri_jubah", 1);
		}
	}

	private Perlengkapan() {
	}
}

package dev.kultivator.formasi.keeper;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.formasi.catalog.CatalogB;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.sect.hall.SectRanks;
import org.jetbrains.annotations.Nullable;

/**
 * Keeper affinity (DESIGN 24.2.8): +0.10 to P when the keeper's Dao or a root element matches the family. Killing arrays
 * follow the spec param {@code kind} (sword, heaven and earth thunder, blaze fire, water; any of them otherwise). Support and grand
 * arrays match only a Tetua Agung.
 */
public final class Affinity {

	public static final double BONUS = 0.10D;

	public static boolean matches(FormationSpec spec, @Nullable Dao dao, int rootMask, int sectRank) {
		return switch (spec.family()) {
			case PERINGATAN, PENYEMBUNYI -> dao == Dao.SHADOW || has(rootMask, Element.WIND);
			case PENGUMPUL, PEMULIHAN -> dao == Dao.LIFE || has(rootMask, Element.WOOD);
			case PENEMPAAN -> dao == Dao.FIRE || has(rootMask, Element.FIRE);
			case ILUSI -> dao == Dao.SPACE || has(rootMask, Element.EARTH);
			case KENDALI, PELINDUNG -> dao == Dao.BODY || has(rootMask, Element.EARTH);
			case PERPINDAHAN -> dao == Dao.SPACE || has(rootMask, Element.SPACE);
			case PENANGKAL -> dao == Dao.LIGHTNING || has(rootMask, Element.LIGHTNING) || has(rootMask, Element.METAL);
			case PEMBUNUH -> killing(spec.params().getInt("kind", -1), dao, rootMask);
			case PENGUAT, GABUNGAN -> sectRank >= SectRanks.TETUA_AGUNG;
		};
	}

	private static boolean killing(int kind, @Nullable Dao dao, int rootMask) {
		boolean sword = dao == Dao.SWORD || has(rootMask, Element.METAL);
		boolean thunder = dao == Dao.LIGHTNING || has(rootMask, Element.LIGHTNING);
		boolean fire = dao == Dao.FIRE || has(rootMask, Element.FIRE);
		boolean water = dao == Dao.ICE || has(rootMask, Element.WATER) || has(rootMask, Element.ICE);
		return switch (kind) {
			case CatalogB.KILL_SWORD -> sword;
			case CatalogB.KILL_HEAVEN, CatalogB.KILL_EARTH -> thunder;
			case CatalogB.KILL_BLAZE -> fire;
			case CatalogB.KILL_WATER -> water;
			default -> sword || thunder || fire || water;
		};
	}

	private static boolean has(int mask, Element element) {
		return (mask & element.bit()) != 0;
	}

	private Affinity() {
	}
}

package dev.kultivator.sekte.paviliun;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kitab Silsilah, the sect's book of lineage (DESIGN 36.2.5): the graves a full cemetery made room from, oldest first, so
 * no fallen disciple is ever forgotten. An enshrined one keeps its spirit tablet on the Altar Leluhur (the blessing counts
 * the tablets of both lists). Beyond {@code silsilahMaks} records the oldest are folded into a plain count ("and N more
 * of the early generations") rather than dropped without a word.
 */
final class Silsilah {

	/** Adds a record (its tombstone slot cleared). */
	static void add(UUID owner, PaviliunData.Grave grave) {
		List<PaviliunData.Grave> list = new ArrayList<>(PaviliunData.lineage(owner));
		list.removeIf(g -> g.soul().equals(grave.soul()));
		list.add(grave.withSlot(-1));
		int folded = 0;
		int max = Paviliun.config().silsilahMaks;
		while (list.size() > max) {
			// fold the oldest record that holds no tablet; if all hold one, the oldest
			int victim = 0;
			for (int i = 0; i < list.size(); i++) {
				if (!list.get(i).enshrined()) {
					victim = i;
					break;
				}
			}
			list.remove(victim);
			folded++;
		}
		PaviliunData.setLineage(owner, list);
		if (folded > 0) {
			PaviliunData.addFolded(owner, folded);
		}
	}

	/** Spirit tablets on the altar: the enshrined graves and lineage records. */
	static int tablets(UUID owner) {
		int n = 0;
		for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
			if (g.enshrined()) {
				n++;
			}
		}
		for (PaviliunData.Grave g : PaviliunData.lineage(owner)) {
			if (g.enshrined()) {
				n++;
			}
		}
		return n;
	}

	/** Enshrines the grave or lineage record of {@code soul}; false when not found or already enshrined. */
	static boolean enshrine(UUID owner, UUID soul) {
		List<PaviliunData.Grave> graves = new ArrayList<>(PaviliunData.graves(owner));
		for (int i = 0; i < graves.size(); i++) {
			PaviliunData.Grave g = graves.get(i);
			if (g.soul().equals(soul)) {
				if (g.enshrined()) {
					return false;
				}
				graves.set(i, g.enshrine());
				PaviliunData.setGraves(owner, graves);
				return true;
			}
		}
		List<PaviliunData.Grave> lineage = new ArrayList<>(PaviliunData.lineage(owner));
		for (int i = 0; i < lineage.size(); i++) {
			PaviliunData.Grave g = lineage.get(i);
			if (g.soul().equals(soul)) {
				if (g.enshrined()) {
					return false;
				}
				lineage.set(i, g.enshrine());
				PaviliunData.setLineage(owner, lineage);
				return true;
			}
		}
		return false;
	}

	/** The record (grave first, then lineage) of {@code soul}, or null. */
	static PaviliunData.Grave find(UUID owner, UUID soul) {
		for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
			if (g.soul().equals(soul)) {
				return g;
			}
		}
		for (PaviliunData.Grave g : PaviliunData.lineage(owner)) {
			if (g.soul().equals(soul)) {
				return g;
			}
		}
		return null;
	}

	private Silsilah() {
	}
}

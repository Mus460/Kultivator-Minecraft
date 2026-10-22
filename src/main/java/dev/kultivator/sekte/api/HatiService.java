package dev.kultivator.sekte.api;

import dev.kultivator.disciple.DiscipleData;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Module P, Kepribadian &amp; Kesetiaan (DESIGN 36.2.1, contract C36.P1): the disciple's traits, its contentment
 * (Kepuasan 0-100) and the loyalty P derives from contentment and the existing master bond ({@code DisciplePaths.bond}).
 * Everything reads {@code extra.hati}; only P writes it. Others report treatment through {@code SekteBus}
 * ({@code DISCIPLE_MISTREATED}, {@code DISCIPLE_HONORED}), never by calling P. The default has no traits and full
 * loyalty, so every module behaves as before while P is missing or off.
 */
public interface HatiService {

	/** The loyalty states, from content to gone (P's thresholds, DESIGN 36.2.1). */
	enum Keadaan {
		SETIA, GELISAH, MEMBANGKANG, AKAN_PERGI
	}

	HatiService NONE = new HatiService() {
	};

	/** The disciple's traits (2, sometimes 3), empty while P is off. */
	default List<Sifat> traits(DiscipleData data) {
		return List.of();
	}

	default boolean has(DiscipleData data, Sifat sifat) {
		return traits(data).contains(sifat);
	}

	/** Contentment 0-100 (70 when P is off). */
	default int contentment(DiscipleData data) {
		return 70;
	}

	/** Loyalty 0-100: P's blend of contentment and the master bond (100 when P is off). */
	default int loyalty(DiscipleData data) {
		return 100;
	}

	default Keadaan state(DiscipleData data) {
		return Keadaan.SETIA;
	}

	/** True while the disciple refuses orders and duties (Membangkang; activity {@code SekteIds.ACT_SULK}). */
	default boolean defiant(DiscipleData data) {
		return state(data) == Keadaan.MEMBANGKANG || state(data) == Keadaan.AKAN_PERGI;
	}

	/**
	 * P's multiplier on work output for other modules (W's garden and mine, A's study speed, X's mission roll): 0.6-1.3,
	 * from diligence and contentment. 1 when P is off.
	 */
	default double workFactor(DiscipleData data) {
		return 1.0D;
	}

	/** One line for the disciple scroll and the hub ("Rajin, Setia · Puas 82"), or null when P is off. */
	default @Nullable Component summary(DiscipleData data) {
		return null;
	}
}

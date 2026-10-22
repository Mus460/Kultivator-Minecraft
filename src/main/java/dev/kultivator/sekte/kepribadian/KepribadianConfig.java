package dev.kultivator.sekte.kepribadian;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteIds;

/**
 * Section {@code kepribadian} of {@code config/kultivator-sekte.json} (DESIGN 36.2.1): module P's switches and numbers.
 * Read once from {@link Kepribadian#init()}; {@code /kultivasi uji perguruan muat} copies new values into the same object.
 */
public final class KepribadianConfig {

	/** Off: no traits, no contentment, no departures (the saved data stays; the service answers as if P were missing). */
	public boolean aktif = true;
	/** Off: no disciple ever leaves on its own (the warnings and defiance still happen). Expelling stays possible. */
	public boolean bolehPergi = true;
	/** Loyalty below this is "Akan Pergi" (a Setia disciple uses this minus 5). */
	public int ambangPergi = 15;
	/** In-game days in a row below the threshold before a disciple leaves. */
	public int hariPergi = 3;
	/** In-game days without any care (teaching, a pill, a word, a gift, shared meditation) before "Diabaikan". */
	public int neglectDays = 5;
	/** 0-2: multiplies every trait effect (1 = as designed, 0 = traits only for show). */
	public double faktorWatak = 1.0D;
	/** Seconds between two counted blows of the master (a flurry is one blow). */
	public int pukulCooldownDetik = 10;
	/** Thought bubbles: the server sends moods of disciples within this many blocks of a player (8-48). */
	public int jarakGelembung = 24;

	private static KepribadianConfig current = new KepribadianConfig();

	static KepribadianConfig load() {
		current = SekteConfig.section(SekteIds.P, KepribadianConfig.class, KepribadianConfig::new, KepribadianConfig::clamp);
		return current;
	}

	public static KepribadianConfig get() {
		return current;
	}

	private static KepribadianConfig clamp(KepribadianConfig c) {
		c.ambangPergi = Math.max(5, Math.min(40, c.ambangPergi));
		c.hariPergi = Math.max(1, Math.min(30, c.hariPergi));
		c.neglectDays = Math.max(1, Math.min(60, c.neglectDays));
		c.faktorWatak = Double.isFinite(c.faktorWatak) ? Math.max(0.0D, Math.min(2.0D, c.faktorWatak)) : 1.0D;
		c.pukulCooldownDetik = Math.max(1, Math.min(600, c.pukulCooldownDetik));
		c.jarakGelembung = Math.max(8, Math.min(48, c.jarakGelembung));
		return c;
	}

	/** {@code 1 + (factor - 1) * faktorWatak}: a trait factor scaled by the config. */
	public static double scaled(double factor) {
		return 1.0D + (factor - 1.0D) * current.faktorWatak;
	}
}

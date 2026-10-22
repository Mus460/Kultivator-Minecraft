package dev.kultivator.sekte.turnamen;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteIds;

/**
 * Section {@code turnamen} of {@code config/kultivator-sekte.json} (DESIGN 36.2.3). Every switch only adds to the old
 * {@code sect.event.Tournament}; its schedule, rewards and the Elite every third stay as they are.
 */
public final class TurnamenConfig {

	/** Each bout is a Sword Duel on the lei tai (else the old ring bout of {@code DuelArena}). */
	public boolean pakaiDuelPedang = true;
	/** An empty-handed fighter is lent a wooden training sword for its bout (taken back afterwards). */
	public boolean pinjamPedang = true;
	/** Visiting spectators (allied envoys, vassal villagers) at most, 0-12. */
	public int penontonMaks = 12;
	/** Envoys of allied NPC sects among the spectators, at most 0-4. */
	public int utusanMaks = 4;
	/** Villagers of vassal villages among the spectators, at most 0-6. */
	public int wargaMaks = 6;
	/** Idle disciples who are not entrants take the remaining tribune seats, at most 0-16. */
	public int muridPenontonMaks = 8;
	/** NPC sects may challenge the hall to an inter-sect tournament (module D's TANTANGAN). */
	public boolean antarSekte = true;
	/** The Elo K factor (8-64). */
	public int eloK = 32;
	/** The tribune component at the arena of templates 6-9 (off: no new component is planned; built ones stay). */
	public boolean tribun = true;
	/** The final and champion scenes of this module (beats 120-121) replace the old tournament beats. */
	public boolean adegan = true;
	/** Seconds of a Sword Duel bout (60-180); the final gets 30 more. */
	public int detikBabak = 90;

	private static TurnamenConfig current = new TurnamenConfig();

	static void load() {
		current = SekteConfig.section(SekteIds.T, TurnamenConfig.class, TurnamenConfig::new, TurnamenConfig::clamp);
	}

	public static TurnamenConfig get() {
		return current;
	}

	private static TurnamenConfig clamp(TurnamenConfig c) {
		c.penontonMaks = Math.max(0, Math.min(12, c.penontonMaks));
		c.utusanMaks = Math.max(0, Math.min(4, c.utusanMaks));
		c.wargaMaks = Math.max(0, Math.min(6, c.wargaMaks));
		c.muridPenontonMaks = Math.max(0, Math.min(16, c.muridPenontonMaks));
		c.eloK = Math.max(8, Math.min(64, c.eloK));
		c.detikBabak = Math.max(60, Math.min(180, c.detikBabak));
		return c;
	}
}

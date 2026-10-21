package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.data.Element;

/**
 * The glyphs module R shows on parts and doors (common code: the server picks an index, the client draws the text
 * Display). Trigrams in the Early Heaven (Fu Xi) order, which is also their Unicode order U+2630-U+2637: Qian (Heaven),
 * Dui (Lake), Li (Fire), Zhen (Thunder), Xun (Wind), Kan (Water), Gen (Mountain), Kun (Earth). The five phases in the
 * generating cycle Wood, Fire, Earth, Metal, Water.
 */
public final class Glyphs {

	public static final String[] TRIGRAMS = {"☰", "☱", "☲", "☳", "☴", "☵", "☶", "☷"};
	/** Colours of the trigrams' images (heaven gold, lake teal, fire red, thunder violet, wind green, water blue, mountain ochre, earth brown). */
	public static final int[] TRIGRAM_RGB = {0xFFE27A, 0x7FE8E0, 0xFF6A3A, 0xB57CFF, 0x9CFFB0, 0x4F9BFF, 0xC8A060, 0xA07848};

	/** The generating cycle (DESIGN 34.5.5 P2, elite/ElementRelations.generates). */
	public static final Element[] CYCLE = {Element.WOOD, Element.FIRE, Element.EARTH, Element.METAL, Element.WATER};
	public static final String[] PHASE = {"木", "火", "土", "金", "水"};

	/** Door glyphs: 0 seal, 1-5 the five phases (index + 1), 6 the tower, 7 the prison. */
	public static final String SEAL = "封";
	public static final String TOWER = "塔";
	public static final String PRISON = "獄";
	public static final String STAR = "✦";

	public static final int DOOR_SEAL = 0;
	public static final int DOOR_PHASE = 1;
	public static final int DOOR_TOWER = 6;
	public static final int DOOR_PRISON = 7;

	/** The text of a door glyph index. */
	public static String door(int index) {
		if (index >= DOOR_PHASE && index < DOOR_PHASE + PHASE.length) {
			return PHASE[index - DOOR_PHASE];
		}
		return switch (index) {
			case DOOR_TOWER -> TOWER;
			case DOOR_PRISON -> PRISON;
			default -> SEAL;
		};
	}

	/** The position of {@code element} in {@link #CYCLE}, -1 for the mutant elements. */
	public static int phaseOf(Element element) {
		for (int i = 0; i < CYCLE.length; i++) {
			if (CYCLE[i] == element) {
				return i;
			}
		}
		return -1;
	}

	private Glyphs() {
	}
}

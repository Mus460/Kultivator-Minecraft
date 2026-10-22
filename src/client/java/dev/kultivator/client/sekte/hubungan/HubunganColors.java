package dev.kultivator.client.sekte.hubungan;

import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.api.Ikatan;

/**
 * The colours and seal glyphs of the kinds of tie, shared by the Hubungan tab and the Jaring Hubungan screen: jade for
 * friends, gold for sworn kin, rose for lovers and Dao companions, amber for rivals, red for grudges.
 */
final class HubunganColors {

	static int of(Ikatan kind) {
		return switch (kind) {
			case SAHABAT -> 0x3FAE72;
			case SAUDARA_ANGKAT -> 0xD9A030;
			case KEKASIH -> 0xF07AA8;
			case PASANGAN_DAO -> 0xE0407E;
			case SAINGAN -> 0xE08A3A;
			case DENDAM -> 0xC8302A;
			case KENALAN -> 0x8AA096;
			case NONE -> 0x9A9A9A;
		};
	}

	/** One CJK seal per kind: acquaintance, friend, righteousness (sworn kin), love, companion, contest, enmity. */
	static String seal(Ikatan kind) {
		return switch (kind) {
			case KENALAN -> "识";
			case SAHABAT -> "友";
			case SAUDARA_ANGKAT -> "义";
			case KEKASIH -> "恋";
			case PASANGAN_DAO -> "侣";
			case SAINGAN -> "争";
			case DENDAM -> "仇";
			case NONE -> "·";
		};
	}

	/** The scale from grudge red through ink grey to friendship jade and love rose. */
	static int affinity(int a) {
		if (a <= -60) {
			return 0xC8302A;
		}
		if (a < 0) {
			return Palette.mix(0xFF8A8A8A, 0xFFE08A3A, -a / 60.0F) & 0xFFFFFF;
		}
		if (a < 40) {
			return Palette.mix(0xFF8A8A8A, 0xFF3FAE72, a / 40.0F) & 0xFFFFFF;
		}
		if (a < 75) {
			return Palette.mix(0xFF3FAE72, 0xFFD9A030, (a - 40) / 35.0F) & 0xFFFFFF;
		}
		return Palette.mix(0xFFD9A030, 0xFFE0407E, Math.min(1.0F, (a - 75) / 25.0F)) & 0xFFFFFF;
	}

	private HubunganColors() {
	}
}

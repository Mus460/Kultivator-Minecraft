package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceDisciples;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The script of the feud scenes (D-39F): every scene line is spoken over the matching 39.F.7 voice event (the Japanese
 * bark of its kind: a taunt, a retort, a challenge...) but captioned with one of several written lines, so a quarrel
 * over a stolen scroll does not read like one over a broken tea cup. Caption keys live in
 * {@code lang-src/sekte_mandiri_seteru.*} under {@code naskah.}; a client without the key shows no caption and still
 * hears the voice (the voice layer checks {@code Language.has}).
 * <ul>
 * <li>{@code naskah.<sebab>.<n>.a|b|c}: the exchange of a quarrel per cause (39.F.2): their opener, our answer, their
 * sting ({@code gulungan_kita} when our disciple took the scroll); {@code naskah.rendah.<n>} replaces our answer when
 * our disciple is humble.</li>
 * <li>Families with {@link #VARIAN} variants: tegang, palak, lindungi, curi, duel.tantang/terima/menang/kalah,
 * tawuran, sergap, patroli_guru, maaf, sumpah, dendam, lerai, ganti_rugi, ketahuan; two-voice families
 * {@code damai/saingan/kekasih.<n>.a|b}.</li>
 * </ul>
 * Variants are picked from the feud id and its history length, so one feud keeps telling its own story and replays of
 * the same rung do not repeat word for word.
 */
final class Naskah {

	/** Exchanges per cause. */
	static final int VARIAN_SEBAB = 3;
	/** Variants of every other family. */
	static final int VARIAN = 4;

	/** A stable variant for {@code f} and a salt (same feud, same rung: the same words; the next rung moves on). */
	static int pilih(@Nullable Perseteruan f, String salt, int n) {
		if (f == null) {
			return 0;
		}
		int h = f.id.hashCode() * 31 + salt.hashCode();
		return Math.floorMod(h * 0x9E3779B1 + f.riwayat.size() * 7, n);
	}

	/** {@code naskah.<keluarga>.<variant>} for feud {@code f}. */
	static String baris(@Nullable Perseteruan f, String keluarga) {
		return SeteruIds.k("naskah." + keluarga + "." + pilih(f, keluarga, VARIAN));
	}

	/** {@code naskah.<keluarga>.<variant>.<sisi>} of a two-voice family. */
	static String baris2(@Nullable Perseteruan f, String keluarga, char sisi) {
		return SeteruIds.k("naskah." + keluarga + "." + pilih(f, keluarga, VARIAN) + "." + sisi);
	}

	/** {@code naskah.<sebab>.<n>.<bagian>} of the quarrel over {@code f}'s cause. */
	static String sebab(Perseteruan f, char bagian) {
		String sebab = f.sebab == null || f.sebab.isEmpty() ? SeteruIds.SEBAB_PERISTIWA : f.sebab;
		if (SeteruIds.SEBAB_GULUNGAN.equals(sebab) && SeteruIds.SALAH_KITA.equals(f.salah)) {
			// our disciple took the scroll: they accuse, ours makes excuses
			sebab = "gulungan_kita";
		}
		return SeteruIds.k("naskah." + sebab + "." + pilih(f, "sebab", VARIAN_SEBAB) + "." + bagian);
	}

	/** Our humble disciple's answer instead of a retort. */
	static String rendah(Perseteruan f) {
		return SeteruIds.k("naskah.rendah." + pilih(f, "rendah", VARIAN));
	}

	/**
	 * {@code body} (ours or theirs) speaks the disciple voice line {@code line} (or its {@link Adegan#cadangan} fallback
	 * while the clip does not exist) with the caption {@code caption}.
	 */
	static void ucap(@Nullable DiscipleEntity body, String line, String caption) {
		if (body == null || body.isRemoved() || !Voice.free(body)) {
			return;
		}
		String use = VoiceEvents.exists("disciple.m1." + line) ? line : Adegan.cadangan(line);
		Voice.say(body, "disciple." + VoiceDisciples.voiceOf(body) + "." + use, caption);
	}

	/** An elder body speaks {@code elderKey} (fallback {@code elder.welcome}) with the caption {@code caption}. */
	static void tetua(@Nullable Entity body, String elderKey, String caption) {
		if (body != null && !body.isRemoved()) {
			Voice.say(body, SekteSuara.key(elderKey, "elder.welcome"), caption);
		}
	}

	private Naskah() {
	}
}

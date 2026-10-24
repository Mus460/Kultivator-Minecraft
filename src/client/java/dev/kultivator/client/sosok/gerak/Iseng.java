package dev.kultivator.client.sosok.gerak;

import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import org.jetbrains.annotations.Nullable;

import static dev.kultivator.client.sosok.gerak.Klip.*;

/**
 * Idle gestures (DESIGN 40.5.9): only after the body has stood in its idle stance for 200 ticks, one gesture every
 * 160-320 ticks (seeded by the entity and the time): looking around, shifting weight, straightening a sleeve
 * (148 variant 1), brushing dust off the robe, a glance at the sky, scratching the head (children, the lazy), a deep
 * sigh; women tuck a strand behind the ear, old men stroke the beard, elders cough, children kick a pebble; by trait
 * the diligent cut the air once (128),
 * the brave stretch their shoulders, the lazy yawn (148) about every 600 ticks. They play in the reaction layer (7), so
 * any server clip, activity, cast or duel above wins at once.
 */
final class Iseng {

	private static final Klip LIHAT_SEKELILING = Klip.baru(0, "iseng_lihat_sekeliling", 60).sekali().lapisan(Lapisan.KEPALA)
			.kunci(0, HY, 0).kunci(15, HY, 30).kunci(25, HY, 30).kunci(40, HY, -30).kunci(50, HY, -30).kunci(60, HY, 0).bangun();
	private static final Klip PINDAH_TUMPUAN = Klip.baru(0, "iseng_pindah_tumpuan", 50).sekali()
			.kunci(0, BZ, 0, RLZ, 0, LLZ, 0).kunci(15, BZ, 3, RLZ, 4, LLZ, -2).kunci(35, BZ, 3, RLZ, 4, LLZ, -2)
			.kunci(50, BZ, 0, RLZ, 0, LLZ, 0).bangun();
	private static final Klip GARUK_KEPALA = Klip.baru(0, "iseng_garuk_kepala", 40).sekali()
			.kunci(0, RAX, 0, RAY, 0, RAZ, 0, HZ, 0).kunciE(8, Easing.CUBIC_OUT, RAX, -150, RAY, -30, RAZ, 20, HZ, 6)
			.kunci(32, RAX, -150, RAY, -30, RAZ, 20, HZ, 6).kunci(40, RAX, 0, RAY, 0, RAZ, 0, HZ, 0).osc(RAZ, 6, 6).bangun();
	private static final Klip HELA_NAPAS = Klip.baru(0, "iseng_hela_napas", 40).sekali().lapisan(Lapisan.ATAS)
			.kunci(0, BAHU, 0, LEAN, 0, NAPAS, 1).kunci(10, BAHU, 0.6F, LEAN, -3, NAPAS, 2).kunci(30, BAHU, 0, LEAN, 2, NAPAS, 2)
			.kunci(40, BAHU, 0, LEAN, 0, NAPAS, 1).wajahPada(10, -1, Wajah.MULUT_O).wajahPada(30, -1, Wajah.MULUT_DIAM).bangun();
	private static final Klip REGANG_BAHU = Klip.baru(0, "iseng_regang_bahu", 40).sekali()
			.kunci(0, RAZ, 0, LAZ, 0, RAX, 0, LAX, 0, LEAN, 0, BAHU, 0)
			.kunci(15, RAZ, 30, LAZ, -30, RAX, 10, LAX, 10, LEAN, -6, BAHU, 0.5F)
			.kunci(25, RAZ, 30, LAZ, -30, RAX, 10, LAX, 10, LEAN, -6, BAHU, 0.5F)
			.kunci(40, RAZ, 0, LAZ, 0, RAX, 0, LAX, 0, LEAN, 0, BAHU, 0).bangun();
	/** Women: the right hand tucks a strand behind the ear, the head tilting into it. */
	private static final Klip SELIPKAN_RAMBUT = Klip.baru(0, "iseng_selipkan_rambut", 40).sekali()
			.kunci(0, RAX, 0, RAY, 0, RAZ, 0, HZ, 0, HX, 0)
			.kunciE(10, Easing.CUBIC_OUT, RAX, -160, RAY, -50, RAZ, 10, HZ, 8, HX, 4)
			.kunci(18, RAX, -152, RAY, -42, RAZ, 6, HZ, 7).kunci(26, RAX, -158, RAY, -48, RAZ, 10, HZ, 8)
			.kunci(40, RAX, 0, RAY, 0, RAZ, 0, HZ, 0, HX, 0).bangun();
	/** Old men: the left hand strokes the beard down, slowly, chin lifted. */
	private static final Klip ELUS_JENGGOT = Klip.baru(0, "iseng_elus_jenggot", 60).sekali()
			.kunci(0, LAX, 0, LAY, 0, HX, 0).kunciE(10, Easing.CUBIC_OUT, LAX, -100, LAY, 30, HX, -6)
			.kunci(20, LAX, -88, LAY, 26).kunci(28, LAX, -100, LAY, 30).kunci(38, LAX, -88, LAY, 26).kunci(46, LAX, -100, LAY, 30, HX, -6)
			.kunci(60, LAX, 0, LAY, 0, HX, 0).bangun();
	/** Elders: a dry cough into the fist, the shoulders jerking twice. */
	private static final Klip BATUK = Klip.baru(0, "iseng_batuk", 30).sekali()
			.kunci(0, LEAN, 0, HX, 0, RAX, 0, RAY, 0, BAHU, 0)
			.kunciE(4, Easing.CUBIC_OUT, LEAN, 12, HX, 10, RAX, -110, RAY, -40, BAHU, 0.4F).kunci(8, LEAN, 4, BAHU, 0)
			.kunciE(12, Easing.CUBIC_OUT, LEAN, 12, HX, 10, BAHU, 0.4F).kunci(16, LEAN, 4, BAHU, 0).kunci(24, RAX, -60, RAY, -20)
			.kunci(30, LEAN, 0, HX, 0, RAX, 0, RAY, 0, BAHU, 0)
			.wajahPada(4, Wajah.MATA_TUTUP, Wajah.MULUT_O).wajahPada(18, Wajah.MATA_BUKA, Wajah.MULUT_DIAM).bangun();
	/** Children: a pebble kicked with the right foot, watched as it rolls away. */
	private static final Klip TENDANG_KERIKIL = Klip.baru(0, "iseng_tendang_kerikil", 30).sekali().bendera(KAKI_PAKSA)
			.kunci(0, RLX, 0, HX, 0, HY, 0, RAX, 0, LAX, 0).kunci(8, RLX, 25, HX, 18, RAX, -15, LAX, 10)
			.kunciE(12, Easing.BACK_OUT, RLX, -50, RAX, 12, LAX, -15).kunci(18, RLX, 0, HX, 12, HY, 15)
			.kunci(30, RLX, 0, HX, 0, HY, 0, RAX, 0, LAX, 0).suara(12, "block.gravel.step", 0.1F, 1.5F).bangun();
	/** Brushing the dust off the left shoulder and sleeve with the right hand. */
	private static final Klip KEBAS_JUBAH = Klip.baru(0, "iseng_kebas_jubah", 36).sekali()
			.kunci(0, RAX, 0, RAY, 0, HY, 0).kunciE(8, Easing.CUBIC_OUT, RAX, -80, RAY, -60, HY, 12)
			.kunci(14, RAX, -50, RAY, -50).kunci(20, RAX, -80, RAY, -60).kunci(26, RAX, -40, RAY, -40, HY, 6)
			.kunci(36, RAX, 0, RAY, 0, HY, 0).suara(14, "block.wool.step", 0.06F, 1.6F).bangun();
	/** A glance up at the sky and back. */
	private static final Klip LIHAT_LANGIT = Klip.baru(0, "iseng_lihat_langit", 60).sekali().lapisan(Lapisan.KEPALA)
			.kunci(0, HX, 0, HZ, 0).kunci(15, HX, -35, HZ, 4).kunci(45, HX, -35, HZ, 4).kunci(60, HX, 0, HZ, 0).bangun();

	/**
	 * Picks the next gesture when one is due ({@code diamLama} = idle in the stance for more than 200 ticks); returns the
	 * clip to start, or null. {@code anak} for children.
	 */
	static @Nullable Klip pilih(GerakKeadaan k, float age, boolean diamLama, Gaya gaya, boolean anak, boolean tua, boolean perempuan) {
		if (!diamLama) {
			if (age > k.isengBerikut) {
				k.isengBerikut = age + 80.0F;
			}
			return null;
		}
		if (age < k.isengBerikut) {
			return null;
		}
		long h = ((long) (age / 200.0F) * 0x9E3779B97F4A7C15L) ^ (k.benih * 0xBF58476DL);
		h ^= h >>> 31;
		int r = (int) Math.floorMod(h, 100L);
		k.isengBerikut = age + 160.0F + Math.floorMod(h >>> 16, 161L);
		boolean malas = gaya.punya(Sifat.MALAS);
		if (malas && r < 35) {
			// the lazy yawn and stretch, the long gap afterward makes it about every 600 ticks
			k.isengBerikut += 300.0F;
			return GerakKatalog.klip(GerakId.MENGUAP_PEREGANGAN);
		}
		if (gaya.punya(Sifat.RAJIN) && r < 25) {
			return GerakKatalog.klip(GerakId.TEBAS_UDARA);
		}
		if (gaya.punya(Sifat.BERANI) && r < 25) {
			return REGANG_BAHU;
		}
		if (tua && r < 34) {
			return r < 20 ? (perempuan ? SELIPKAN_RAMBUT : ELUS_JENGGOT) : BATUK;
		}
		if (anak && r < 25) {
			return TENDANG_KERIKIL;
		}
		if ((anak || malas) && r < 50) {
			return GARUK_KEPALA;
		}
		if (perempuan && r < 20) {
			return SELIPKAN_RAMBUT;
		}
		if (r < 32) {
			return LIHAT_SEKELILING;
		}
		if (r < 48) {
			return PINDAH_TUMPUAN;
		}
		if (r < 60) {
			Klip rapikan = GerakKatalog.klip(GerakId.MENGUAP_PEREGANGAN);
			return rapikan != null ? rapikan.pilih(GerakId.V_MENGUAP_RAPIKAN, 0, 0) : null;
		}
		if (r < 70) {
			return KEBAS_JUBAH;
		}
		if (r < 80) {
			return LIHAT_LANGIT;
		}
		return HELA_NAPAS;
	}

	private Iseng() {
	}
}

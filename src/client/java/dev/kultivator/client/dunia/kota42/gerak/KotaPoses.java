package dev.kultivator.client.dunia.kota42.gerak;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.sosok.gerak.Easing;
import dev.kultivator.client.sosok.gerak.GerakKatalog;
import dev.kultivator.client.sosok.gerak.Klip;
import dev.kultivator.client.sosok.gerak.Osilator;
import dev.kultivator.client.sosok.gerak.PropKatalog;
import dev.kultivator.client.sosok.gerak.Wajah;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.fx.QiParticleOptions;
import dev.kultivator.fx.QiParticles;
import dev.kultivator.sosok.PropId;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import static dev.kultivator.client.sosok.gerak.Klip.ARAH_SASARAN;
import static dev.kultivator.client.sosok.gerak.Klip.BAHU;
import static dev.kultivator.client.sosok.gerak.Klip.BERBARING;
import static dev.kultivator.client.sosok.gerak.Klip.BICARA;
import static dev.kultivator.client.sosok.gerak.Klip.BOB;
import static dev.kultivator.client.sosok.gerak.Klip.BZ;
import static dev.kultivator.client.sosok.gerak.Klip.DUA_TANGAN;
import static dev.kultivator.client.sosok.gerak.Klip.DUDUK;
import static dev.kultivator.client.sosok.gerak.Klip.HX;
import static dev.kultivator.client.sosok.gerak.Klip.HY;
import static dev.kultivator.client.sosok.gerak.Klip.HZ;
import static dev.kultivator.client.sosok.gerak.Klip.JALAN_OK;
import static dev.kultivator.client.sosok.gerak.Klip.KAKI_PAKSA;
import static dev.kultivator.client.sosok.gerak.Klip.KERJA;
import static dev.kultivator.client.sosok.gerak.Klip.LAX;
import static dev.kultivator.client.sosok.gerak.Klip.LAY;
import static dev.kultivator.client.sosok.gerak.Klip.LAZ;
import static dev.kultivator.client.sosok.gerak.Klip.LEAN;
import static dev.kultivator.client.sosok.gerak.Klip.LIFT;
import static dev.kultivator.client.sosok.gerak.Klip.LIRIK_ATAS;
import static dev.kultivator.client.sosok.gerak.Klip.LIRIK_BAWAH;
import static dev.kultivator.client.sosok.gerak.Klip.LLX;
import static dev.kultivator.client.sosok.gerak.Klip.LLY;
import static dev.kultivator.client.sosok.gerak.Klip.LLZ;
import static dev.kultivator.client.sosok.gerak.Klip.MATA_TUTUP;
import static dev.kultivator.client.sosok.gerak.Klip.MOD_DETAK;
import static dev.kultivator.client.sosok.gerak.Klip.MOD_GEMETAR;
import static dev.kultivator.client.sosok.gerak.Klip.MOD_NAPAS_X;
import static dev.kultivator.client.sosok.gerak.Klip.NAPAS;
import static dev.kultivator.client.sosok.gerak.Klip.PROP_SLOT_KANAN;
import static dev.kultivator.client.sosok.gerak.Klip.RAX;
import static dev.kultivator.client.sosok.gerak.Klip.RAY;
import static dev.kultivator.client.sosok.gerak.Klip.RAZ;
import static dev.kultivator.client.sosok.gerak.Klip.RLX;
import static dev.kultivator.client.sosok.gerak.Klip.RLY;
import static dev.kultivator.client.sosok.gerak.Klip.RLZ;
import static dev.kultivator.client.sosok.gerak.Klip.SINKRON;
import static dev.kultivator.client.sosok.gerak.Klip.SY_ANAK;
import static dev.kultivator.client.sosok.gerak.Klip.TILT;
import static dev.kultivator.client.sosok.gerak.Klip.TWIST;
import static dev.kultivator.dunia.kota42.api.KotaGerakId.*;

/**
 * The body language of Kota yang Hidup on the shared Gerak system (DESIGN 42.12.5 and 42.4A.7, contract C42.V1): the hand
 * props 170-239 of {@code dunia.kota42.api.KotaGerakId} (item models {@code kultivator:fx/kota42/<name>} made by
 * {@code tools/gen_kota_hidup_prop.py}) with their grips, registered through {@link PropKatalog#daftar}; the town clips
 * 350-449 and the 42S spread and micro-break clips 450-459, registered through {@link GerakKatalog#daftar} under
 * {@link KotaGerakId#namaKatalog}; and the client effect hooks their {@code kota42:*} KAIT events fire (the lamplighter's
 * taper, spirit money in the air, the paper fire, a root test's aura, ribbon confetti, sweat, the sugar thread, flies,
 * beaten-out dust, the cast net's splash, a cough).
 * <p>
 * Format and channels exactly as 38.N.5 ({@code PoseLibrary}, degrees; LIFT in blocks); the controller blends 6 ticks, the
 * springs ride on top, and on Minimal detail it holds one static pose. Once clips have the lengths of
 * {@link KotaGerakId#durasiSekali} (the server times them by it) and start and end at rest; loops close on themselves.
 * Clips several bodies play together ({@link Klip#SINKRON}: pyramid, lion dancers, palanquin and coffin bearers, the
 * wedding bows, the hug, the grandchild, the brawl, finger guessing) take their time from the server so
 * {@code SosokApi.gerakSinkron(e, klip, t0)} with one {@code t0} keeps them in step. The player gets no new clip
 * (42.12.5: players use vanilla and DESIGN 40 poses in ceremonies), so the {@code PlayerModelMixin} is untouched.
 * <p>
 * Nothing here changes the world; effects scale with {@code EffectQuality}, none at Minimal or in a shader shadow pass.
 * Client thread only; called once from {@code KotaHidupClient.init}.
 */
public final class KotaPoses {

	// ================================================================== effect hook ids (KAIT)

	static final String K_NYALA = "kota42:nyala";
	static final String K_KERTAS = "kota42:kertas";
	static final String K_BAKAR = "kota42:bakar";
	static final String K_AURA = "kota42:aura";
	static final String K_KONFETI = "kota42:konfeti";
	static final String K_KERINGAT = "kota42:keringat";
	static final String K_GULA = "kota42:gula";
	static final String K_LALAT = "kota42:lalat";
	static final String K_DEBU_BAJU = "kota42:debu_baju";
	static final String K_JALA = "kota42:jala";
	static final String K_BATUK = "kota42:batuk";
	static final String K_AIR = "kota42:air";
	private static final String[] KAIT = {K_NYALA, K_KERTAS, K_BAKAR, K_AURA, K_KONFETI, K_KERINGAT, K_GULA, K_LALAT, K_DEBU_BAJU,
			K_JALA, K_BATUK, K_AIR};

	// ================================================================== shared poses (degrees, blocks)

	/** Rest: every upper-body channel these clips touch, at zero. */
	private static final float[] NOL = {RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, LAZ, 0, LEAN, 0, TWIST, 0, HX, 0, HY, 0, LIFT, 0};
	/** Seated on a stool or bench (DUDUK_KURSI's legs). */
	private static final float[] DUDUK_BANGKU = {RLX, -90, LLX, -90, RLY, 4, LLY, -4, LIFT, -0.5F};
	/** Cross-legged on the floor (DUDUK_LANTAI's legs). */
	private static final float[] DUDUK_LESEHAN = {RLX, -80, LLX, -80, RLY, 35, LLY, -35, RLZ, 20, LLZ, -20, LIFT, -0.65F};
	/** A deep squat, heels down, knees apart. */
	private static final float[] JONGKOK = {RLX, -100, LLX, -96, RLZ, 10, LLZ, -10, LIFT, -0.5F};
	/** On both knees, sitting back a little. */
	private static final float[] BERLUTUT = {RLX, 62, LLX, 62, RLZ, 6, LLZ, -6, LIFT, -0.4F};
	/** Fist in palm before the chest (baoquan, the numbers of 40's HORMAT). */
	private static final float[] BAOQUAN = {RAX, -74.5F, RAY, -35.5F, RAZ, 0, LAX, -71.6F, LAY, 35.5F, LAZ, 0};
	/** Both hands behind the back. */
	private static final float[] TANGAN_PUNGGUNG = {RAX, 28, RAY, 0, RAZ, -14, LAX, 28, LAY, 0, LAZ, 14};

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		props();
		kebiasaan();
		rombongan();
		jalanan();
		upacara();
		kedaiDanBalai();
		bangunDanFestival();
		anakDesaMusim();
		ragam();
		for (String id : KAIT) {
			GerakKatalog.daftarKait(id, (e, st, t) -> efek(id, e, st.bodyRot));
		}
	}

	// ================================================================== props 170-239

	private static void props() {
		for (int id = PROP_FIRST; id <= PROP_LAST; id++) {
			String model = KotaGerakId.propModel(id);
			if (model != null) {
				PropKatalog.daftar(id, Kultivator.id(model), pegangan(id));
			}
		}
	}

	private static PropKatalog.Pegangan g(float dx, float dy, float dz, float rx, float skala, int sifat) {
		return new PropKatalog.Pegangan(dx, dy, dz, rx, 0.0F, 0.0F, skala, sifat);
	}

	/**
	 * Hand grips (anchor (8, 8, 8) of the models = the grip): jars, cups, cages, baskets, buckets, lanterns, trays and the
	 * erhu stay upright whatever the arm does ({@link PropKatalog#TEGAK}); sticks, fans, brushes, horns and slips point
	 * forward out of the fist (rx 90); the broom and the umbrella are long props; ground and body props (drum, stake,
	 * beam, mat, water jar) are placed by the clips' attach points and keep the default grip.
	 */
	private static @Nullable PropKatalog.Pegangan pegangan(int id) {
		int tegak = PropKatalog.TEGAK;
		int besarTegak = PropKatalog.TEGAK | PropKatalog.BESAR;
		return switch (id) {
			case PROP_GUCI_ARAK -> g(0, -0.5F, -1.0F, 0, 0.9F, besarTegak);
			case PROP_CAWAN_ARAK -> g(0, 0, -0.8F, 0, 1.0F, tegak);
			case PROP_SANGKAR_BURUNG, PROP_SANGKAR_AYAM, PROP_JARING_IKAN, PROP_LENTERA_KELINCI, PROP_KOTAK_OBAT_KELILING,
					PROP_KERANJANG_TELUR_MERAH, PROP_BATA_TUMPUK, PROP_DACING, PROP_GONG_TANGAN, PROP_KENTONGAN, PROP_BUNTELAN ->
					g(0, 0.4F, 0, 0, 1.0F, besarTegak);
			case PROP_KARUNG_BERAS, PROP_EMBER_API, PROP_KENDI_SUP -> g(0, 0.3F, 0, 0, 0.9F, besarTegak);
			case PROP_GUCI_JANGKRIK -> g(0, -0.5F, -1.2F, 0, 0.9F, tegak);
			case PROP_ERHU -> g(0, 0, -1.5F, 0, 1.0F, besarTegak);
			case PROP_BINGKAI_SULAM, PROP_KUE_BULAN, PROP_MANGKUK_BUBUR, PROP_KUE_KERANJANG, PROP_DADU_CAWAN -> g(0, -0.5F, 0, 0, 0.9F, tegak);
			case PROP_SELENDANG, PROP_KUPLET_MERAH, PROP_IKAN, PROP_KANTONG_KUE, PROP_PITA_MERAH -> g(0, 0.3F, 0, 0, 1.0F, tegak);
			case PROP_UANG_KERTAS, PROP_PLESTER_HARIMAU, PROP_CAP_PEJABAT -> g(0, -0.3F, 0, 0, 1.0F, tegak);
			case PROP_CAWAN_BAMBU -> g(0, 0, -1.0F, 0, 0.9F, tegak);
			case PROP_BAYI_BEDONG -> g(0, 1.0F, 0, 0, 1.0F, besarTegak);
			case PROP_GENTENG_TUMPUK -> g(0, 0, 0, 0, 1.0F, besarTegak);
			case PROP_BOLA_CUJU -> g(0, -1.6F, -1.0F, 0, 1.0F, tegak);
			case PROP_PIPA -> g(0, 0, -1.5F, 0, 1.0F, besarTegak);
			case PROP_NAMPAN_TEH_BESAR -> g(0, -1.0F, -3.0F, 0, 1.0F, besarTegak);
			case PROP_SIMPAI -> g(0, 0, 0, 0, 1.0F, besarTegak);
			case PROP_GALAH_LENTERA -> g(0, -2.0F, 0, 90, 1.0F, PropKatalog.PANJANG | PropKatalog.BESAR);
			case PROP_SAPU_LIDI -> g(0, -6.0F, 0, 180, 1.0F, PropKatalog.PANJANG | PropKatalog.BESAR);
			case PROP_PAYUNG_MERAH -> g(0, -10.0F, 0, 0, 1.0F, PropKatalog.PANJANG | PropKatalog.BESAR);
			case PROP_PIRING_AKROBAT -> g(0, -1.0F, 0, 90, 1.0F, PropKatalog.BESAR);
			case PROP_LAYANG_NAGA, PROP_LAYANG_KUPU -> g(0, 0, 0, 90, 1.0F, PropKatalog.BESAR);
			case PROP_KUAS_BESAR -> g(0, 0, 0, -118, 1.0F, 0);
			case PROP_ALMANAK_MERAH -> g(0, 0, -1.0F, 60, 0.9F, 0);
			case PROP_CERMIN_TANGAN, PROP_SENDOK_GULA, PROP_PERMEN_GULA, PROP_BUNGA, PROP_TULANG, PROP_SUONA, PROP_BALOK_KAYU,
					PROP_ANGPAO, PROP_KIPAS_TARI, PROP_DIZI, PROP_RANTING_DEDALU, PROP_WAYANG_TONGKAT, PROP_SURAT_UTANG,
					PROP_KWITANSI, PROP_GULUNGAN_PERKARA, PROP_GUNTING_BESAR, PROP_KERINCING_BAYI, PROP_TANGHULU ->
					g(0, 0, -1.0F, 90, 0.9F, 0);
			default -> null;
		};
	}

	// ================================================================== builders

	private static Klip.Pembangun b(int id, int durasi) {
		return Klip.baru(id, namaKatalog(id), durasi);
	}

	/** Concatenates channel/value pairs. */
	private static float[] p(float[]... bagian) {
		int n = 0;
		for (float[] a : bagian) {
			n += a.length;
		}
		float[] out = new float[n];
		int i = 0;
		for (float[] a : bagian) {
			System.arraycopy(a, 0, out, i, a.length);
			i += a.length;
		}
		return out;
	}

	private static float[] v(float... pasangan) {
		return pasangan;
	}

	private static void d(Klip.Pembangun k) {
		GerakKatalog.daftar(k.bangun());
	}

	// ================================================================== 350-371: habits, idlers and street arts

	private static void kebiasaan() {
		// 350: the drunkard's swig: the jar hangs in the left hand, the cup tips up to the lips, head back, a hold, a sigh
		d(b(MINUM_ARAK, 60).loop().wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, RAX, -40, RAY, -18, RAZ, 0, LAX, -30, LAZ, -6, HX, 4, LEAN, 0)
				.kunciE(15, Easing.CUBIC_OUT, RAX, -95, RAY, -30, HX, -25, LEAN, -6)
				.kunci(25, RAX, -98, HX, -27, LEAN, -7)
				.kunciE(34, Easing.CUBIC_OUT, RAX, -45, RAY, -18, HX, 6, LEAN, 2)
				.kunci(44, HX, 10, LEAN, 3).kunci(60, RAX, -40, HX, 4, LEAN, 0)
				.osc(TILT, 2.0F, 60.0F).osc(HZ, 3.0F, 30.0F)
				.suara(17, "entity.generic.drink", 0.2F, 0.9F).suara(36, "entity.player.burp", 0.12F, 0.8F)
				.wajahPada(14, Wajah.MATA_TUTUP, Wajah.MULUT_O).wajahPada(30, Wajah.MATA_SETENGAH, Wajah.MULUT_SENYUM)
				.wajahPada(52, -1, Wajah.MULUT_DIAM)
				.prop(PROP_CAWAN_ARAK, PROP_GUCI_ARAK, 0));

		// 351: the stagger home: twist and roll on a sine, a sideways lean, the head lolling, the cup still in hand
		d(b(JALAN_MABUK, 40).loop().bendera(JALAN_OK).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -30, RAZ, 14, LAX, -8, LAZ, -18, HX, 8)
				.osc(TWIST, 12.0F, 40.0F).osc(TILT, 8.0F, 40.0F, 1.57F, Osilator.Bentuk.SIN).osc(HX, 10.0F, 27.0F)
				.osc(HZ, 8.0F, 40.0F, 0.6F, Osilator.Bentuk.SIN).osc(BZ, 3.0F, 20.0F).osc(LAZ, -10.0F, 20.0F)
				.osc(RAX, 8.0F, 13.0F, 0.0F, Osilator.Bentuk.SIN).osc(LEAN, 4.0F, 33.0F)
				.suara(12, "entity.player.burp", 0.08F, 0.7F)
				.wajahPada(20, Wajah.MATA_TUTUP, -1).wajahPada(24, Wajah.MATA_SETENGAH, -1)
				.prop(PROP_CAWAN_ARAK, 0, 0));

		// 352: dead asleep on a bench, one arm hanging off the edge swaying a little, snoring; variant: sprawled on a
		// tavern floor, arms flung out
		d(b(TIDUR_BANGKU, 120).loop().bendera(BERBARING | MATA_TUTUP | KAKI_PAKSA).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_TIDUR)
				.kunci(0, RAX, 30, RAZ, 18, LAX, -95, LAY, 20, RLX, -20, LLX, -45, HX, 10, HZ, 22)
				.osc(RAX, 3.0F, 120.0F).osc(LIFT, 0.012F, 60.0F).osc(BAHU, 0.3F, 60.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF)
				.suara(30, "entity.player.breath", 0.1F, 0.55F).suara(90, "entity.player.breath", 0.1F, 0.5F)
				.wajahPada(60, -1, Wajah.MULUT_O).wajahPada(80, -1, Wajah.MULUT_TIDUR)
				.varian(V_TIDUR_LANTAI, v -> v.kunci(0, RAX, -160, RAZ, 40, LAX, -150, LAZ, -50, RLX, 0, LLX, -25, RLZ, 12, LLZ, -8,
						HX, -8, HZ, -14).osc(LIFT, 0.012F, 60.0F).osc(HZ, 4.0F, 120.0F)));

		// 353: a kibitzer over the chess game: bent forward, hands clasped behind, head following the pieces, now and
		// then a small pointed finger at the board
		d(b(TONTON_CATUR, 80).loop().bendera(LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, p(TANGAN_PUNGGUNG, v(LEAN, 15, HX, 25, HY, 0)))
				.kunci(20, HY, 10).kunci(40, HY, -8).kunci(56, HY, 0)
				.kunci(58, RAX, 28, RAY, 0, RAZ, -14).kunciE(62, Easing.CUBIC_OUT, RAX, -55, RAY, -10, RAZ, 6, LEAN, 20)
				.kunci(70, RAX, -55, LEAN, 20).kunci(76, RAX, 28, RAY, 0, RAZ, -14, LEAN, 15)
				.wajahPada(62, Wajah.MATA_BUKA, Wajah.MULUT_A).wajahPada(70, Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM));

		// 354: hanging the bird cage on a branch: lifting it overhead, hooking it on at t20, letting go, a pleased look up
		d(b(GANTUNG_SANGKAR, 40).sekali().bendera(LIRIK_ATAS).wajah(Wajah.MATA_SENANG, -1)
				.kunci(0, p(NOL, v(RAX, -40)))
				.kunciE(12, Easing.CUBIC_OUT, RAX, -150, RAY, -8, HX, -30, LIFT, 0.04F)
				.kunci(20, RAX, -156, HX, -32, LIFT, 0.06F)
				.kunci(30, RAX, -60, RAY, 0, HX, -24, LIFT, 0)
				.kunci(40, p(NOL))
				.propPada(24, PROP_SLOT_KANAN, 0).suara(20, "block.chain.place", 0.2F, 1.4F)
				.wajahPada(30, -1, Wajah.MULUT_SENYUM)
				.prop(PROP_SANGKAR_BURUNG, 0, 0));

		// 355: the erhu on a stool: the neck upright on the thigh in the left hand, the bow arm sawing out and back, the
		// head swaying with the tune, eyes half shut
		d(b(MAIN_ERHU, 32).loop().bendera(DUDUK | KAKI_PAKSA).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, p(DUDUK_BANGKU, v(LAX, -60, LAY, 18, LAZ, -6, RAX, -50, RAY, -25, RAZ, 0, HX, 10, LEAN, 6)))
				.kunciE(16, Easing.SINE_IO, RAX, -20, RAY, 25, RAZ, 12)
				.kunciE(32, Easing.SINE_IO, RAX, -50, RAY, -25, RAZ, 0)
				.osc(HZ, 6.0F, 32.0F).osc(HX, 3.0F, 16.0F).osc(TWIST, 4.0F, 32.0F).osc(LAX, 2.0F, 8.0F)
				.partikel(0, "minecraft:note", 1, 0)
				.suara(0, "block.note_block.didgeridoo", 0.12F, 1.9F).suara(16, "block.note_block.didgeridoo", 0.12F, 1.7F)
				.wajahPada(16, Wajah.MATA_TUTUP_TENANG, -1).wajahPada(28, Wajah.MATA_SETENGAH, -1)
				.prop(0, PROP_ERHU, 0));

		// 356: embroidery on a stool: the hoop in the left hand, the needle pushed in (t8), the thread drawn out wide (t16)
		d(b(MENYULAM, 24).loop().bendera(DUDUK | KAKI_PAKSA | KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, p(DUDUK_BANGKU, v(LAX, -55, LAY, 22, RAX, -60, RAY, -16, RAZ, 0, HX, 28, LEAN, 12)))
				.kunciE(8, Easing.CUBIC_IN, RAX, -40, RAY, -20)
				.kunciE(16, Easing.CUBIC_OUT, RAX, -70, RAY, 20, RAZ, 24, HX, 22)
				.kunci(24, RAX, -60, RAY, -16, RAZ, 0, HX, 28)
				.osc(HZ, 2.0F, 48.0F)
				.prop(0, PROP_BINGKAI_SULAM, 0));

		// 357: the vain one at a hand mirror: mirror held up before the face, tidying the hair, turning to see both sides
		d(b(BERCERMIN, 60).loop().wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, LAX, -90, LAY, 30, LAZ, 0, RAX, -40, RAY, -10, RAZ, 0, HX, -4, TWIST, 0)
				.kunciE(20, Easing.CUBIC_OUT, RAX, -120, RAY, -30, RAZ, 20, TWIST, 8, HY, 12)
				.kunci(30, RAZ, 32).kunci(40, RAX, -120, RAZ, 20, TWIST, -8, HY, -12)
				.kunci(50, RAX, -40, RAY, -10, RAZ, 0, TWIST, 0, HY, 0)
				.osc(HZ, 4.0F, 30.0F)
				.wajahPada(24, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(42, Wajah.MATA_LIRIK_KIRI, -1).wajahPada(52, Wajah.MATA_SENANG, -1)
				.prop(0, PROP_CERMIN_TANGAN, 0));

		// 358: the miser counting coins: both hands over the purse, fingers flicking in turn, lips moving, a glance round
		d(b(HITUNG_KOIN, 20).loop().bendera(LIRIK_BAWAH | BICARA).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, RAX, -55, RAY, -20, RAZ, 0, LAX, -55, LAY, 20, LAZ, 0, HX, 20, LEAN, 8)
				.kunciE(5, Easing.TANGGA, RAZ, 6).kunciE(10, Easing.TANGGA, RAZ, 0, LAZ, -6).kunciE(15, Easing.TANGGA, LAZ, 0, RAZ, 6)
				.kunciE(20, Easing.TANGGA, RAZ, 0)
				.osc(HY, 6.0F, 80.0F)
				.suara(5, "entity.experience_orb.pickup", 0.04F, 1.9F).suara(15, "entity.experience_orb.pickup", 0.04F, 1.8F)
				.prop(PropId.KANTONG_UANG, 0, 0));

		// 359: the generous one setting a coin down in a begging bowl: a lean, the hand lowered, a little nod
		d(b(BERI_KOIN, 24).sekali().wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL))
				.kunciE(8, Easing.CUBIC_OUT, LEAN, 20, RAX, -70, RAY, -8, HX, 18)
				.kunci(14, RAX, -40, LEAN, 22, HX, 22)
				.kunci(18, LEAN, 14, HX, 12)
				.kunci(24, p(NOL))
				.propPada(14, PROP_SLOT_KANAN, 0)
				.partikel(14, "kilau", 2, 0xFFD050).suara(14, "entity.experience_orb.pickup", 0.08F, 1.5F)
				.prop(PropId.KANTONG_UANG, 0, 0));

		// 360: the bookworm reading on the move: a slow amble, the book in both hands, the head down, a page turned
		d(b(JALAN_BACA, 40).loop().bendera(JALAN_OK | DUA_TANGAN | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -60, RAY, -25, LAX, -60, LAY, 25, HX, 25, LEAN, 4)
				.kunci(34, LAY, 25).kunciE(37, Easing.CUBIC_OUT, LAY, 40).kunci(40, LAY, 25)
				.osc(BZ, 1.5F, 40.0F).osc(HX, 2.0F, 20.0F)
				.suara(37, "item.book.page_turn", 0.15F, 1.1F)
				.pasang(PropId.KITAB, Klip.Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F));

		// 361: crouching to feed a stray cat a fish: down on the heels, holding it out, letting it go, a gentle pat
		d(b(BERI_MAKAN_KUCING, 40).sekali().bendera(KAKI_PAKSA | LIRIK_BAWAH).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL, v(RLX, 0, LLX, 0, RAX, -20)))
				.kunciE(10, Easing.CUBIC_OUT, p(JONGKOK, v(LEAN, 50, RAX, -40, HX, 30)))
				.kunci(18, RAX, -20, LIFT, -0.4F)
				.kunci(24, RAX, -26, RAY, -10).kunci(28, RAX, -18, RAY, 6).kunci(32, RAX, -26, RAY, -10)
				.kunci(40, p(NOL, v(RLX, 0, LLX, 0, RLZ, 0, LLZ, 0)))
				.propPada(20, PROP_SLOT_KANAN, 0).suara(20, "entity.item.pickup", 0.1F, 1.3F)
				.prop(PROP_IKAN, 0, 0));

		// 362: rattling the dice cup: shaken hard beside the ear, slammed mouth-down on the table at t18, a breath held
		d(b(LEMPAR_DADU, 30).sekali().mods(MOD_DETAK, 0.4F).wajah(Wajah.MATA_KILAT, Wajah.MULUT_MENYERINGAI)
				.kunci(0, p(NOL, v(RAX, -50)))
				.kunciE(4, Easing.CUBIC_OUT, RAX, -80, RAY, -20, HX, -6)
				.kunci(6, RAZ, 20).kunci(8, RAZ, -20).kunci(10, RAZ, 20).kunci(12, RAZ, -20).kunci(14, RAZ, 20).kunci(16, RAZ, 0)
				.kunciE(18, Easing.CUBIC_IN, RAX, -40, RAY, -6, LEAN, 14, HX, 18)
				.kunci(26, RAX, -42, LEAN, 14, HX, 22)
				.kunci(30, p(NOL))
				.suara(6, "block.bamboo.hit", 0.15F, 1.6F).suara(10, "block.bamboo.hit", 0.15F, 1.7F)
				.suara(14, "block.bamboo.hit", 0.15F, 1.6F).suara(18, "block.wood.hit", 0.35F, 0.9F)
				.wajahPada(18, Wajah.MATA_KAGET, Wajah.MULUT_DIAM)
				.prop(PROP_CAWAN_BAMBU, 0, 0));

		// 363: the loser's slap on the forehead, a groan and a stagger back
		d(b(MENEPUK_JIDAT, 20).sekali().wajah(Wajah.MATA_TUTUP, Wajah.MULUT_SEDIH)
				.kunci(0, p(NOL))
				.kunciE(6, Easing.BACK_OUT, RAX, -150, RAY, -30, RAZ, 10, HX, 15, LEAN, 8)
				.kunci(12, HX, -10, LEAN, -6)
				.kunci(16, RAX, -140)
				.kunci(20, p(NOL))
				.suara(6, "entity.player.attack.weak", 0.15F, 1.2F)
				.wajahPada(12, Wajah.MATA_SEDIH, Wajah.MULUT_A));

		// 364: the yangge dance: three steps forward and one back, arms swinging high in turn, the fan twirling, hips
		// swaying; variant: the long silk sash flung in figure-eights instead of the fan
		d(b(TARI_YANGGE, 40).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, -110, RAY, -10, RAZ, 20, LAX, -60, LAZ, -12, TWIST, -20, RLX, -22, LLX, 10, LIFT, 0.03F, BZ, -3)
				.kunci(10, RAX, -60, RAZ, 8, LAX, -110, LAZ, -20, TWIST, 20, RLX, 10, LLX, -22, LIFT, 0, BZ, 3)
				.kunci(20, RAX, -110, RAZ, 20, LAX, -60, LAZ, -12, TWIST, -20, RLX, -22, LLX, 10, LIFT, 0.03F, BZ, -3)
				.kunci(30, RAX, -70, RAZ, 14, LAX, -70, LAZ, -14, TWIST, 0, RLX, 16, LLX, 16, LIFT, 0, BZ, 0)
				.kunci(40, RAX, -110, RAZ, 20, LAX, -60, LAZ, -12, TWIST, -20, RLX, -22, LLX, 10, LIFT, 0.03F, BZ, -3)
				.osc(RAY, 30.0F, 10.0F).osc(HZ, 6.0F, 20.0F)
				.suara(0, "block.note_block.snare", 0.12F, 1.2F).suara(10, "block.note_block.snare", 0.12F, 1.1F)
				.suara(20, "block.note_block.snare", 0.12F, 1.2F).suara(30, "block.note_block.basedrum", 0.15F, 1.0F)
				.prop(PROP_KIPAS_TARI, 0, 0)
				.varian(V_YANGGE_SELENDANG, v -> v.warisi().tanpaProp().prop(PROP_SELENDANG, PROP_SELENDANG, 0)
						.osc(LAY, 30.0F, 10.0F, 1.57F, Osilator.Bentuk.SIN)));

		// 365: the sugar painter at the marble slab: leaning in, the ladle pouring a golden line that sways out and back
		d(b(LUKIS_GULA, 30).loop().bendera(KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, LEAN, 25, HX, 30, RAX, -60, RAY, -20, LAX, -30, LAZ, -10)
				.kunci(15, RAY, 20).kunci(30, RAY, -20)
				.osc(RAZ, 6.0F, 10.0F).osc(TWIST, 4.0F, 30.0F)
				.kait(0, K_GULA).kait(10, K_GULA).kait(20, K_GULA)
				.prop(PROP_SENDOK_GULA, 0, 0));

		// 366: peeking over a fence on tiptoe: hands on the rail, chin up, a dart left and right, ducking down at t30
		d(b(INTIP, 40).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_KAGET, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -80, RAY, -10, LAX, -80, LAY, 10, LIFT, 0.08F, LEAN, 10, HX, -5, HY, 0)
				.kunci(10, HY, 18).kunci(20, HY, -18).kunci(26, HY, 0)
				.kunciE(30, Easing.CUBIC_OUT, LIFT, 0.0F, HX, 10)
				.kunci(34, LIFT, 0.0F).kunciE(40, Easing.CUBIC_OUT, LIFT, 0.08F, HX, -5)
				.wajahPada(10, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(20, Wajah.MATA_LIRIK_KIRI, -1).wajahPada(30, Wajah.MATA_KAGET, -1));

		// 367: awe at a passing cultivator: eyes up, hands lifting a little, a small hop; children clasp both hands and
		// bounce twice
		d(b(KAGUM, 30).sekali().bendera(LIRIK_ATAS).wajah(Wajah.MATA_KAGET, Wajah.MULUT_O)
				.kunci(0, p(NOL))
				.kunciE(6, Easing.CUBIC_OUT, HX, -15, RAX, -40, RAZ, 14, LAX, -40, LAZ, -14, LIFT, 0.05F)
				.kunci(10, LIFT, 0).kunci(22, HX, -18, RAX, -42, LAX, -42)
				.kunci(30, p(NOL))
				.wajahPada(14, Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.varian(SY_ANAK, v -> v.kunci(0, p(NOL)).kunciE(5, Easing.CUBIC_OUT, HX, -20, RAX, -80, RAY, -30, LAX, -80, LAY, 30)
						.kunci(8, LIFT, 0.12F).kunci(12, LIFT, 0).kunci(16, LIFT, 0.12F).kunci(20, LIFT, 0)
						.kunci(30, p(NOL)).wajahPada(8, Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)));

		// 368: a big lazy yawn: arms stretched up and back, the mouth wide, shoulders dropping with a sigh
		d(b(MENGUAP, 40).sekali().wajah(Wajah.MATA_MENGANTUK, -1)
				.kunci(0, p(NOL))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -170, RAZ, 14, LAX, -170, LAZ, -14, HX, -20, LEAN, -8)
				.kunci(25, RAX, -175, LAX, -175, HX, -24, LEAN, -10)
				.kunciE(32, Easing.CUBIC_IN, RAX, -20, RAZ, 0, LAX, -20, LAZ, 0, HX, 6, LEAN, 2)
				.kunci(40, p(NOL))
				.suara(12, "entity.player.breath", 0.15F, 0.6F)
				.wajahPada(8, Wajah.MATA_TUTUP, Wajah.MULUT_KAGET).wajahPada(28, Wajah.MATA_MENGANTUK, Wajah.MULUT_DIAM));

		// 369: the poet declaiming: one arm thrown open to the sky, the other hand on the chest, a step forward
		d(b(DEKLAMASI, 60).sekali().bendera(BICARA | LIRIK_ATAS).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, p(NOL, v(RLX, 0, LLX, 0)))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -130, RAY, 10, RAZ, 30, LAX, -40, LAY, 40, HX, -10, RLX, -14, LLX, 8)
				.kunci(30, RAX, -138, RAZ, 34, HX, -14)
				.kunci(40, RAX, -120, RAZ, 26, HY, 10)
				.kunci(52, RAX, -132, HY, -6)
				.kunci(60, p(NOL, v(RLX, 0, LLX, 0)))
				.wajahPada(12, Wajah.MATA_TUTUP_TENANG, -1).wajahPada(40, Wajah.MATA_LIRIK_ATAS, -1));

		// 370: the lamplighter: the pole reached up to the lantern, held while the taper catches (t25), lowered
		d(b(NYALAKAN_LENTERA, 40).sekali().bendera(LIRIK_ATAS).wajah(Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(RAX, -40, LAX, -30)))
				.kunciE(15, Easing.CUBIC_OUT, RAX, -160, RAY, -10, LAX, -150, LAY, 10, HX, -35, LEAN, -6, LIFT, 0.03F)
				.kunci(30, RAX, -162, LAX, -152, HX, -36, LIFT, 0.03F)
				.kunci(40, p(NOL, v(RAX, -40, LAX, -30)))
				.kait(25, K_NYALA).suara(25, "item.flintandsteel.use", 0.2F, 1.2F)
				.wajahPada(26, Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.prop(PROP_GALAH_LENTERA, 0, 0));

		// 371: the storyteller on his stool: wide gestures, then at t0 and t30 the wooden block cracked on the table
		d(b(DONGENG, 60).loop().bendera(DUDUK | KAKI_PAKSA | BICARA).wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, p(DUDUK_BANGKU, v(RAX, -50, RAY, -10, LAX, -40, LAY, 20, HX, 0, LEAN, 6)))
				.kunciE(3, Easing.CUBIC_IN, RAX, -90).kunciE(5, Easing.BACK_OUT, RAX, -50)
				.kunci(14, LAX, -90, LAZ, -30, HY, -14).kunci(22, LAX, -60, LAZ, -10, HY, 10)
				.kunci(30, RAX, -90).kunciE(33, Easing.BACK_OUT, RAX, -50)
				.kunci(44, LAX, -100, LAZ, -40, HY, 12, HX, -8).kunci(54, LAX, -40, LAZ, 0, HY, 0, HX, 0)
				.osc(RAZ, 6.0F, 20.0F)
				.suara(3, "block.wood.hit", 0.5F, 1.3F).suara(33, "block.wood.hit", 0.5F, 1.2F)
				.wajahPada(3, Wajah.MATA_KAGET, Wajah.MULUT_O).wajahPada(14, Wajah.MATA_LIRIK_KIRI, -1)
				.wajahPada(33, Wajah.MATA_MARAH, Wajah.MULUT_A).wajahPada(44, Wajah.MATA_SENANG, -1)
				.prop(PROP_BALOK_KAYU, 0, 0));
	}

	// ================================================================== 372-380: acrobats, lion dance, morning exercise

	private static void rombongan() {
		// 372: plate spinning: both sticks held straight up, wrists trembling to keep the plates turning, a slow sway
		d(b(AKROBAT_PIRING, 40).loop().bendera(LIRIK_ATAS | KAKI_PAKSA).wajah(Wajah.MATA_BUKA, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -170, RAZ, 8, LAX, -170, LAZ, -8, HX, -25, RLZ, 8, LLZ, -8)
				.osc(RAZ, 4.0F, 3.0F).osc(LAZ, -4.0F, 3.0F, 1.0F, Osilator.Bentuk.SIN).osc(TWIST, 6.0F, 40.0F).osc(BZ, 2.0F, 20.0F)
				.prop(PROP_PIRING_AKROBAT, PROP_PIRING_AKROBAT, 0));

		// 373: through the hoop: a run-up, the dive forward (the renderer rolls the body through the somersault), a crouched
		// landing, arms up for applause
		d(b(AKROBAT_SIMPAI, 30).sekali().bendera(KAKI_PAKSA).wajah(Wajah.MATA_KILAT, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(RLX, 0, LLX, 0)))
				.kunci(3, RLX, -40, LLX, 30, RAX, 30, LAX, -30, LEAN, 15)
				.kunci(6, RLX, 30, LLX, -40, RAX, -30, LAX, 30, LEAN, 20)
				.kunci(9, RLX, -40, LLX, 30, RAX, 30, LAX, -30, LEAN, 25)
				.kunciE(12, Easing.CUBIC_OUT, LEAN, 60, RAX, -170, LAX, -170, RLX, 20, LLX, 20, LIFT, 0.3F)
				.kunci(18, LEAN, 70, RLX, -100, LLX, -100, LIFT, 0.5F, RAX, -60, LAX, -60)
				.kunciE(24, Easing.CUBIC_IN, LEAN, 30, RLX, -70, LLX, -70, LIFT, -0.2F, RAX, -30, LAX, -30)
				.kunciE(27, Easing.BACK_OUT, LEAN, 0, RLX, 0, LLX, 0, LIFT, 0, RAX, -160, RAZ, 30, LAX, -160, LAZ, -30)
				.kunci(30, p(NOL, v(RLX, 0, LLX, 0)))
				.suara(12, "entity.player.attack.sweep", 0.15F, 1.4F).suara(24, "entity.player.big_fall", 0.12F, 1.3F)
				.wajahPada(27, Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA));

		// 374-376: the human pyramid, three acrobats in step: the base in a deep horse stance holding the others up, the
		// middle on one leg on the base's shoulders, the top above with arms spread, swaying to balance
		d(b(PIRAMID_BAWAH, 60).loop().bendera(KAKI_PAKSA | SINKRON).mods(MOD_GEMETAR, 0.4F).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, RLZ, 30, LLZ, -30, RLX, -30, LLX, -30, RLY, 10, LLY, -10, LIFT, -0.28F, RAX, -150, RAZ, 12, LAX, -150, LAZ, -12,
						HX, -10, NAPAS, 1.6F)
				.osc(TWIST, 1.5F, 60.0F).osc(BAHU, 0.3F, 20.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF));
		d(b(PIRAMID_TENGAH, 60).loop().bendera(KAKI_PAKSA | SINKRON).wajah(Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				.kunci(0, RLX, 0, LLX, -70, LLZ, -10, LIFT, 1.6F, RAX, -90, RAZ, 70, LAX, -150, LAZ, -12, HX, -10)
				.osc(TILT, 2.0F, 60.0F, 1.57F, Osilator.Bentuk.SIN).osc(RAZ, 6.0F, 30.0F));
		d(b(PIRAMID_ATAS, 60).loop().bendera(KAKI_PAKSA | SINKRON | LIRIK_ATAS).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, RLX, 0, LLX, 0, RLZ, 4, LLZ, -4, LIFT, 3.0F, RAX, -10, RAZ, 88, LAX, -10, LAZ, -88, HX, -14)
				.osc(TILT, 3.0F, 60.0F).osc(RAZ, 8.0F, 20.0F).osc(LAZ, -8.0F, 20.0F, 1.0F, Osilator.Bentuk.SIN)
				.suara(0, "entity.villager.celebrate", 0.12F, 1.4F));

		// 377: stilt walking: long slow strides (the egrang gear lengthens the legs), arms spread for balance, a wobble
		d(b(EGRANG_JALAN, 30).loop().bendera(JALAN_OK).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -20, RAZ, 70, LAX, -20, LAZ, -70, LEAN, 4, HX, -6)
				.osc(RAZ, 10.0F, 30.0F).osc(LAZ, 10.0F, 30.0F).osc(TILT, 3.0F, 30.0F).osc(BOB, 0.6F, 15.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF));

		// 378-379: the two dancers inside the lion: the head dancer crouches and springs, arms up inside the head; the tail
		// dancer bent double behind, hands on the head dancer's waist (only the legs show under the cloth)
		d(b(BARONGSAI_KEPALA, 40).loop().bendera(KAKI_PAKSA | SINKRON)
				.kunci(0, RLX, -40, LLX, -40, RLZ, 14, LLZ, -14, LIFT, -0.3F, RAX, -150, LAX, -150, LEAN, 10)
				.kunciE(12, Easing.CUBIC_OUT, RLX, -10, LLX, -10, LIFT, 0.25F, LEAN, -8, RAX, -170, LAX, -170)
				.kunciE(20, Easing.CUBIC_IN, RLX, -40, LLX, -40, LIFT, -0.3F, LEAN, 10, RAX, -150, LAX, -150)
				.kunci(30, TWIST, 15).kunci(36, TWIST, -15).kunci(40, TWIST, 0)
				.suara(20, "block.wool.place", 0.25F, 0.8F));
		d(b(BARONGSAI_EKOR, 40).loop().bendera(KAKI_PAKSA | SINKRON)
				.kunci(0, RLX, -50, LLX, -50, RLZ, 14, LLZ, -14, LIFT, -0.35F, LEAN, 70, HX, -40, RAX, -110, LAX, -110, RAZ, -10, LAZ, 10)
				.kunciE(12, Easing.CUBIC_OUT, RLX, -20, LLX, -20, LIFT, 0.15F, LEAN, 60)
				.kunciE(20, Easing.CUBIC_IN, RLX, -50, LLX, -50, LIFT, -0.35F, LEAN, 70)
				.kunci(30, BZ, 6).kunci(36, BZ, -6).kunci(40, BZ, 0));

		// 380: leading the morning tai chi before the rows: the 40.5.10 circle at 1.2 times the amplitude, slower, a step
		d(b(PIMPIN, 160).loop().mods(MOD_NAPAS_X, 0).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, RAX, -74.5F, LAX, -74.5F, RAZ, 13.8F, LAZ, -13.8F, RLZ, 8.3F, LLZ, -8.3F, LEAN, 0, LIFT, -0.08F)
				.osc(RAX, 48.1F, 160.0F).osc(LAX, -48.1F, 160.0F).osc(RAZ, 24.0F, 160.0F, 0.0F, Osilator.Bentuk.COS)
				.osc(LAZ, -24.0F, 160.0F, 0.0F, Osilator.Bentuk.COS).osc(LEAN, 2.8F, 160.0F).osc(TWIST, 14.0F, 160.0F)
				.osc(LIFT, 0.04F, 80.0F));
	}

	// ================================================================== 381-393: night watch and street events

	private static void jalanan() {
		// 381: three knocks on a door
		d(b(KETUK_PINTU, 30).sekali().wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, p(NOL))
				.kunciE(4, Easing.CUBIC_OUT, RAX, -80, RAY, -6, LEAN, 4)
				.kunciE(6, Easing.BACK_OUT, RAX, -70).kunci(9, RAX, -80).kunciE(12, Easing.BACK_OUT, RAX, -70).kunci(15, RAX, -80)
				.kunciE(18, Easing.BACK_OUT, RAX, -70).kunci(24, RAX, -78, HY, 8)
				.kunci(30, p(NOL))
				.suara(6, "block.wooden_door.close", 0.15F, 1.6F).suara(12, "block.wooden_door.close", 0.15F, 1.6F)
				.suara(18, "block.wooden_door.close", 0.15F, 1.6F));

		// 382: the watchman's gong: held out in the left hand, struck once with a whip of the right (easeOutBack), the ring
		d(b(PUKUL_GONG, 20).sekali().wajah(Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(LAX, -80, RAX, -60)))
				.kunciE(4, Easing.CUBIC_OUT, RAX, -110, RAZ, 14, TWIST, -6)
				.kunciE(6, Easing.BACK_OUT, RAX, -60, RAZ, 0, TWIST, 6)
				.kunci(14, RAX, -64, TWIST, 0)
				.kunci(20, p(NOL, v(LAX, -80, RAX, -60)))
				.suara(6, "block.bell.use", 0.4F, 1.5F)
				.prop(PropId.PEMUKUL, PROP_GONG_TANGAN, 0));

		// 383: the bamboo watch drum beaten fast as he walks the night streets
		d(b(PUKUL_KENTONGAN, 10).loop().bendera(JALAN_OK).wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, LAX, -70, LAZ, -6, RAX, -90, RAY, -20)
				.kunciE(5, Easing.CUBIC_IN, RAX, -60).kunciE(10, Easing.CUBIC_OUT, RAX, -90)
				.suara(5, "block.bamboo.hit", 0.25F, 1.2F)
				.prop(PropId.PEMUKUL, PROP_KENTONGAN, 0));

		// 384: calling the watch, hand cupped at the mouth, chest out, the lantern raised in the left hand
		d(b(SERU_JAGA, 40).sekali().bendera(BICARA).wajah(Wajah.MATA_BUKA, Wajah.MULUT_A)
				.kunci(0, p(NOL, v(LAX, -40)))
				.kunciE(6, Easing.CUBIC_OUT, RAX, -110, RAY, -55, RAZ, 6, LAX, -60, HX, -15, LEAN, -6)
				.kunci(30, RAX, -108, HX, -14, LEAN, -5)
				.kunci(40, p(NOL, v(LAX, -40)))
				.wajahPada(10, -1, Wajah.MULUT_O).wajahPada(22, -1, Wajah.MULUT_A).wajahPada(34, -1, Wajah.MULUT_DIAM)
				.prop(0, PropId.LENTERA_TANGAN, 0));

		// 385: the pickpocket's bump: a sideways twist into the mark, a little lift, the hand slipping into the purse
		d(b(TABRAK, 10).sekali().wajah(Wajah.MATA_LIRIK_KANAN, Wajah.MULUT_MENYERINGAI)
				.kunci(0, p(NOL))
				.kunciE(4, Easing.CUBIC_OUT, TWIST, 30, LIFT, 0.03F, RAX, -40, RAY, 20, LEAN, 6)
				.kunci(7, RAX, -30, RAY, 26)
				.kunci(10, p(NOL))
				.suara(4, "block.wool.hit", 0.15F, 1.2F));

		// 386: the getaway: bent low, the stolen purse clutched to the chest, a look back over the shoulder every 20 ticks
		d(b(LARI_COPET, 10).loop().bendera(JALAN_OK).wajah(Wajah.MATA_KAGET, Wajah.MULUT_MENYERINGAI)
				.kunci(0, LEAN, 25, RAX, -70, RAY, -30, LAX, 30, LAZ, -10)
				.osc(HY, 60.0F, 20.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).osc(LAX, 30.0F, 10.0F)
				.prop(PropId.KANTONG_UANG, 0, 0));

		// 387-388: a tavern brawl, two mirrored fighters: a wild haymaker, a dodge, a stagger
		float[] kuda = {RLX, -14, LLX, 12, RLZ, 6, LLZ, -6, LIFT, -0.05F};
		d(b(TINJU_KEDAI_A, 40).loop().bendera(KAKI_PAKSA | SINKRON).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MARAH)
				.kunci(0, p(kuda, v(RAX, -60, RAY, -10, LAX, -70, LAY, 10, TWIST, 0, LEAN, 6, HX, 0)))
				.kunciE(6, Easing.CUBIC_OUT, RAX, -120, RAY, 20, TWIST, -20)
				.kunciE(10, Easing.BACK_OUT, RAX, -30, RAY, -30, TWIST, 25, LEAN, 14)
				.kunci(18, RAX, -60, RAY, -10, TWIST, 0, LEAN, 6)
				.kunciE(22, Easing.CUBIC_OUT, TWIST, -25, LEAN, -4, HY, -14)
				.kunci(28, TWIST, 0, HY, 0, LEAN, 6)
				.kunciE(30, Easing.CUBIC_OUT, LEAN, -10, HX, -14, RAX, -20, LAX, -20, TILT, 8)
				.kunci(36, LEAN, 6, HX, 0, RAX, -60, LAX, -70, TILT, 0)
				.suara(10, "entity.player.attack.weak", 0.2F, 0.9F).suara(30, "entity.player.hurt", 0.1F, 1.1F)
				.wajahPada(30, Wajah.MATA_SAKIT, Wajah.MULUT_A).wajahPada(36, Wajah.MATA_MARAH, Wajah.MULUT_MARAH));
		d(b(TINJU_KEDAI_B, 40).loop().bendera(KAKI_PAKSA | SINKRON).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MARAH)
				.kunci(0, p(kuda, v(LAX, -60, LAY, 10, RAX, -70, RAY, -10, TWIST, 0, LEAN, 6, HX, 0)))
				.kunciE(10, Easing.CUBIC_OUT, TWIST, 25, LEAN, -4, HY, 14)
				.kunci(16, TWIST, 0, HY, 0, LEAN, 6)
				.kunciE(26, Easing.CUBIC_OUT, LAX, -120, LAY, -20, TWIST, 20)
				.kunciE(30, Easing.BACK_OUT, LAX, -30, LAY, 30, TWIST, -25, LEAN, 14)
				.kunci(38, LAX, -60, LAY, 10, TWIST, 0, LEAN, 6)
				.kunciE(12, Easing.CUBIC_OUT, TILT, -8, HX, -12).kunci(18, TILT, 0, HX, 0)
				.suara(30, "entity.player.attack.weak", 0.2F, 0.95F).suara(12, "entity.player.hurt", 0.1F, 1.0F)
				.wajahPada(12, Wajah.MATA_SAKIT, Wajah.MULUT_A).wajahPada(18, Wajah.MATA_MARAH, Wajah.MULUT_MARAH));

		// 389: stepping between them: both arms flung out to the sides, looking from one to the other, shouting
		d(b(LERAI, 30).loop().bendera(BICARA | KAKI_PAKSA).wajah(Wajah.MATA_MARAH, Wajah.MULUT_A)
				.kunci(0, RAX, -80, RAZ, 80, LAX, -80, LAZ, -80, RLZ, 10, LLZ, -10, HY, -40, LEAN, 2)
				.kunci(13, HY, -40).kunciE(17, Easing.CUBIC_OUT, HY, 40).kunci(28, HY, 40).kunci(30, HY, -40)
				.osc(RAZ, 6.0F, 15.0F).osc(LAZ, -6.0F, 15.0F)
				.wajahPada(15, -1, Wajah.MULUT_O).wajahPada(25, -1, Wajah.MULUT_A));

		// 390: the bucket chain: twist left to take the bucket, swing it across, twist right to hand it on
		d(b(OPER_EMBER, 24).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, TWIST, -40, LAX, -60, LAZ, 10, RAX, -60, RAY, 20, LEAN, 10, RLZ, 10, LLZ, -10, LIFT, -0.06F)
				.kunci(10, TWIST, -40).kunciE(12, Easing.CUBIC_OUT, TWIST, 0, LAX, -40, RAX, -70)
				.kunci(20, TWIST, 40, RAX, -60, RAY, -20, LAX, -30).kunci(22, TWIST, 40)
				.kunci(24, TWIST, -40, LAX, -60, RAX, -60, RAY, 20)
				.partikel(16, "minecraft:splash", 2, 0).suara(20, "item.bucket.empty", 0.1F, 1.3F)
				.prop(PROP_EMBER_API, 0, 0));

		// 391: dousing the flames: the bucket swung back and flung forward from both hands (t8), water thrown out
		d(b(SIRAM_API, 20).sekali().bendera(DUA_TANGAN).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, p(NOL, v(RAX, -60, LAX, -60)))
				.kunciE(4, Easing.CUBIC_OUT, RAX, -20, LAX, -20, LEAN, -6)
				.kunciE(8, Easing.BACK_OUT, RAX, -120, LAX, -120, LEAN, 16)
				.kunci(14, RAX, -100, LAX, -100, LEAN, 10)
				.kunci(20, p(NOL))
				.kait(8, K_AIR).suara(8, "entity.generic.splash", 0.3F, 1.0F).suara(10, "block.fire.extinguish", 0.25F, 1.2F)
				.prop(PROP_EMBER_API, 0, 0));

		// 392: heaving an overturned cart upright: bent low, both hands under the edge, straining up (trembling)
		d(b(ANGKAT_GEROBAK, 30).loop().bendera(KAKI_PAKSA | KERJA).mods(MOD_GEMETAR, 1.2F).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, LEAN, 30, RAX, -100, LAX, -100, RAY, -10, LAY, 10, RLX, -40, LLX, -40, LIFT, -0.2F, HX, -10, NAPAS, 1.8F)
				.kunciE(20, Easing.CUBIC_IN, LIFT, 0.0F, RLX, -10, LLX, -10, LEAN, 24, RAX, -110, LAX, -110)
				.kunci(30, LIFT, -0.2F, RLX, -40, LLX, -40, LEAN, 30, RAX, -100, LAX, -100)
				.suara(20, "entity.player.breath", 0.2F, 0.8F)
				.wajahPada(20, Wajah.MATA_SAKIT, Wajah.MULUT_A).wajahPada(28, Wajah.MATA_TUTUP, Wajah.MULUT_MENGGIGIT_BIBIR));

		// 393: wailing on the knees: hands in the hair, rocking forward and back
		d(b(MERATAP, 40).loop().bendera(KAKI_PAKSA).mods(MOD_DETAK, 0.5F).wajah(Wajah.MATA_SEDIH, Wajah.MULUT_SEDIH)
				.kunci(0, p(BERLUTUT, v(RAX, -160, RAZ, 20, LAX, -160, LAZ, -20, LEAN, 30, HX, 20)))
				.kunci(20, LEAN, 10, HX, -10).kunci(40, LEAN, 30, HX, 20)
				.osc(HY, 8.0F, 40.0F)
				.suara(20, "entity.player.breath", 0.12F, 1.3F)
				.wajahPada(18, Wajah.MATA_TUTUP, Wajah.MULUT_A).wajahPada(30, Wajah.MATA_SEDIH, Wajah.MULUT_SEDIH));
	}

	// ================================================================== 394-409: wedding, funeral, birth, farewell

	private static void upacara() {
		// 394: a palanquin bearer: the pole on the right shoulder, the steadying hand, the shared bobbing step of four
		d(b(PIKUL_TANDU, 30).loop().bendera(JALAN_OK | SINKRON).wajah(Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -160, RAZ, 22, LAX, -10, LAZ, -8, LEAN, 4, HZ, 4, BAHU, -0.4F)
				.osc(LIFT, 0.02F, 15.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).osc(BZ, 2.0F, 30.0F).osc(LAX, 8.0F, 30.0F));

		// 395: the bride and groom's three bows (heaven and earth, the parents, each other): hands joined at the chest,
		// a deep bow, upright again; the facing comes from the ceremony data
		Klip.Pembangun sembah = b(SEMBAH_NIKAH, 120).sekali().bendera(SINKRON).wajah(Wajah.MATA_LIRIK_BAWAH, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL));
		for (int i = 0; i < 3; i++) {
			int s = 40 * i;
			sembah.kunciE(s + 8, Easing.CUBIC_OUT, BAOQUAN).kunci(s + 10, LEAN, 0, HX, 0)
					.kunciE(s + 20, Easing.CUBIC_OUT, LEAN, 60, HX, 30)
					.kunci(s + 30, LEAN, 60, HX, 30)
					.kunciE(s + 37, Easing.SINE_IO, LEAN, 0, HX, 0);
		}
		d(sembah.kunci(120, p(NOL)).suara(20, "block.note_block.chime", 0.2F, 1.0F).suara(60, "block.note_block.chime", 0.2F, 1.2F)
				.suara(100, "block.note_block.chime", 0.2F, 1.5F).wajahPada(100, Wajah.MATA_MALU, -1).wajahPada(112, Wajah.MATA_SENANG, -1));

		// 396: a toast at the banquet: the cup raised high, a call, the cup drunk off at t15
		d(b(BERSULANG, 30).sekali().bendera(BICARA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, p(NOL, v(RAX, -40)))
				.kunciE(6, Easing.CUBIC_OUT, RAX, -100, RAY, -10, HX, -6)
				.kunci(12, RAX, -104)
				.kunciE(15, Easing.CUBIC_OUT, RAX, -95, RAY, -30, HX, -25)
				.kunci(22, RAX, -60, HX, 0)
				.kunci(30, p(NOL, v(RAX, -40)))
				.suara(15, "entity.generic.drink", 0.2F, 1.1F)
				.wajahPada(15, Wajah.MATA_TUTUP, Wajah.MULUT_O).wajahPada(22, Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.prop(PROP_CAWAN_ARAK, 0, 0));

		// 397: the groom on horseback: legs astride, lifted to the saddle, bobbing with the horse's gait, one hand on the
		// reins, the other waving now and then
		d(b(TUNGGANG_KUDA, 30).loop().bendera(KAKI_PAKSA | DUDUK).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RLX, -45, LLX, -45, RLZ, 25, LLZ, -25, LIFT, 0.9F, RAX, -40, RAY, -14, LAX, -40, LAY, 14, LEAN, 4)
				.osc(LIFT, 0.04F, 15.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).osc(LEAN, 3.0F, 15.0F).osc(HX, 2.0F, 15.0F)
				.suara(0, "entity.horse.step", 0.1F, 1.0F).suara(15, "entity.horse.step", 0.1F, 1.1F));

		// 398: the suona player of the procession: the horn at the lips with both hands, cheeks puffed, head beating time
		d(b(TIUP_SUONA, 20).loop().bendera(JALAN_OK | DUA_TANGAN).mods(MOD_DETAK, 0.3F).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_MENIUP)
				.kunci(0, RAX, -80, RAY, -14, LAX, -80, LAY, 14, HX, -10)
				.osc(HX, 6.0F, 10.0F).osc(TWIST, 4.0F, 20.0F).osc(RAX, 4.0F, 10.0F, 0.0F, Osilator.Bentuk.SIN)
				.osc(LAX, 4.0F, 10.0F, 0.0F, Osilator.Bentuk.SIN)
				.partikel(0, "minecraft:note", 1, 0).suara(0, "block.note_block.didgeridoo", 0.12F, 2.0F)
				.prop(PROP_SUONA, 0, 0));

		// 399: the small drum at the waist, sticks alternating
		d(b(TABUH_GENDERANG_KECIL, 12).loop().bendera(JALAN_OK).wajah(Wajah.MATA_SENANG, -1)
				.kunci(0, RAX, -90, RAY, -10, LAX, -50, LAY, 10)
				.kunciE(6, Easing.CUBIC_IN, RAX, -50, LAX, -90)
				.kunciE(12, Easing.CUBIC_IN, RAX, -90, LAX, -50)
				.osc(HX, 3.0F, 6.0F)
				.suara(0, "block.note_block.basedrum", 0.25F, 1.3F).suara(6, "block.note_block.snare", 0.2F, 1.2F)
				.prop(PropId.PEMUKUL, PropId.PEMUKUL, 0)
				.pasang(PROP_GENDERANG_KECIL, Klip.Titik.PINGGANG_KIRI, -2.6F, 0, -2.6F, 0, 0, 0, 1.0F));

		// 400: a coffin bearer: the palanquin carry, slower, a slight stoop under the weight
		d(b(PIKUL_USUNGAN, 36).loop().bendera(JALAN_OK | SINKRON).wajah(Wajah.MATA_LIRIK_BAWAH, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -160, RAZ, 22, LAX, -10, LAZ, -8, LEAN, 10, HX, 14, HZ, 4, BAHU, -0.5F)
				.osc(LIFT, 0.015F, 18.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).osc(BZ, 1.5F, 36.0F));

		// 401: scattering spirit money over the procession: the hand flung up and out, the paper fluttering
		d(b(TABUR_UANG_KERTAS, 30).sekali().bendera(LIRIK_ATAS).wajah(Wajah.MATA_SEDIH, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(RAX, -70)))
				.kunciE(8, Easing.CUBIC_OUT, RAX, -120, RAZ, 10)
				.kunciE(14, Easing.BACK_OUT, RAX, -160, RAZ, 40, HX, -20)
				.kunci(22, RAX, -110, RAZ, 20, HX, -10)
				.kunci(30, p(NOL, v(RAX, -70)))
				.kait(14, K_KERTAS).suara(14, "item.book.page_turn", 0.2F, 0.7F)
				.prop(PROP_UANG_KERTAS, 0, 0));

		// 402: a mourner: bent in grief, the sleeve wiping the eyes, shoulders shaking with sobs
		d(b(RATAP_DUKA, 60).loop().bendera(JALAN_OK).mods(MOD_DETAK, 0.8F).wajah(Wajah.MATA_SEDIH, Wajah.MULUT_SEDIH)
				.kunci(0, LEAN, 25, HX, 30, RAX, -140, RAY, -40, RAZ, 6, LAX, -20, LAZ, -6)
				.kunci(20, RAX, -130, RAY, -30).kunci(40, RAX, -144, RAY, -44).kunci(60, RAX, -140, RAY, -40)
				.osc(HX, 3.0F, 15.0F).osc(BAHU, 0.4F, 15.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF)
				.suara(10, "entity.player.breath", 0.1F, 1.4F).suara(40, "entity.player.breath", 0.1F, 1.3F)
				.wajahPada(20, Wajah.MATA_TUTUP, -1).wajahPada(30, Wajah.MATA_SEDIH, -1));

		// 403: a baby crawling: on hands and knees, low, limbs alternating
		d(b(MERANGKAK, 20).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, LEAN, 80, LIFT, -0.7F, HX, -60, RAX, -60, LAX, -100, RLX, -70, LLX, -110)
				.kunci(10, RAX, -100, LAX, -60, RLX, -110, LLX, -70)
				.kunci(20, RAX, -60, LAX, -100, RLX, -70, LLX, -110)
				.osc(TWIST, 5.0F, 20.0F).osc(HY, 6.0F, 40.0F));

		// 404: the zhuazhou choice: sitting on the mat, reaching out (t15), the chosen thing lifted up to show (t25)
		d(b(PILIH_BENDA, 30).sekali().bendera(DUDUK | KAKI_PAKSA | LIRIK_BAWAH).wajah(Wajah.MATA_KAGET, Wajah.MULUT_O)
				.kunci(0, p(DUDUK_LESEHAN, v(RAX, -60, LAX, -30, LEAN, 10, HX, 20)))
				.kunciE(15, Easing.CUBIC_OUT, RAX, -90, RAY, -10, LEAN, 30, HX, 30)
				.kunciE(25, Easing.BACK_OUT, RAX, -150, RAY, 0, LEAN, -4, HX, -14)
				.kunci(30, RAX, -140, LEAN, 0, HX, -10)
				.wajahPada(25, Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.pasang(PROP_TIKAR_ZHUAZHOU, Klip.Titik.DEPAN_KAKI, 0, 0, -6, 0, 0, 0, 1.0F));

		// 405: a mother rocking her newborn in her arms, swaying gently, eyes on the baby
		d(b(GENDONG_BAYI, 60).loop().bendera(JALAN_OK | LIRIK_BAWAH).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, LAX, -70, LAY, 40, LAZ, -10, RAX, -60, RAY, -30, RAZ, 6, HX, 24, LEAN, 2)
				.osc(TWIST, 6.0F, 60.0F).osc(HZ, 4.0F, 60.0F, 1.0F, Osilator.Bentuk.SIN).osc(BZ, 1.5F, 60.0F)
				.prop(PROP_BAYI_BEDONG, 0, 0));

		// 406: the father handing out red eggs from the basket
		d(b(BAGI_TELUR, 30).sekali().bendera(BICARA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, p(NOL, v(LAX, -50)))
				.kunciE(8, Easing.CUBIC_OUT, RAX, -60, RAY, 20, TWIST, 14, HY, 10)
				.kunciE(16, Easing.CUBIC_OUT, RAX, -70, RAY, -10, TWIST, -6, LEAN, 10, HY, -6)
				.kunci(24, RAX, -50, LEAN, 4)
				.kunci(30, p(NOL, v(LAX, -50)))
				.prop(0, PROP_KERANJANG_TELUR_MERAH, 0));

		// 407: an embrace between two bodies (a candidate's farewell, a family): arms wrapped round, a lean in, a rock
		d(b(PELUK, 60).sekali().bendera(SINKRON).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -80, RAZ, 40, LAX, -80, LAZ, -40, LEAN, 10, HX, 8)
				.kunci(30, TWIST, 6).kunci(40, TWIST, -6).kunci(48, TWIST, 0)
				.kunci(52, RAX, -80, LAX, -80, LEAN, 10)
				.kunci(60, p(NOL))
				.wajahPada(52, Wajah.MATA_SEDIH, -1));

		// 408: turning back on the road for a last wave, the bundle on the other hand
		d(b(PAMIT_LAMBAI, 40).sekali().wajah(Wajah.MATA_SEDIH, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL, v(LAX, -10)))
				.kunciE(8, Easing.CUBIC_OUT, HY, 60, TWIST, 30)
				.kunciE(12, Easing.CUBIC_OUT, LAX, -160, LAZ, -45)
				.kunci(16, LAZ, 5).kunci(20, LAZ, -45).kunci(24, LAZ, 5).kunci(28, LAZ, -40)
				.kunci(34, LAX, -10, LAZ, 0, HY, 30, TWIST, 15)
				.kunci(40, p(NOL, v(LAX, -10)))
				.prop(PROP_BUNTELAN, 0, 0));

		// 409: the root test: both palms on the stone, head bowed, a breath; the aura rises at t30
		d(b(UJI_AKAR, 60).sekali().bendera(LIRIK_BAWAH).wajah(Wajah.MATA_TUTUP_TENANG, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -75, RAY, -10, LAX, -75, LAY, 10, HX, 20, LEAN, 14)
				.kunci(28, HX, 22, LEAN, 16, NAPAS, 1.4F)
				.kunci(34, HX, 0, LEAN, 6)
				.kunci(50, RAX, -72, LAX, -72)
				.kunci(60, p(NOL))
				.kait(30, K_AURA).kait(38, K_AURA).suara(30, "block.amethyst_block.chime", 0.3F, 1.2F)
				.wajahPada(32, Wajah.MATA_KAGET, Wajah.MULUT_O));
	}

	// ================================================================== 410-419: tavern, theatre, chores, court

	private static void kedaiDanBalai() {
		// 410-411: finger guessing (huaquan), two drinkers in step: fists pumped and flung open with a shout every 10 ticks
		d(b(HUAQUAN_A, 20).loop().bendera(BICARA | SINKRON).wajah(Wajah.MATA_KILAT, Wajah.MULUT_A)
				.kunci(0, RAX, -90, RAY, -10, LAX, -30, LEAN, 4, HX, 0)
				.kunciE(4, Easing.BACK_OUT, RAX, -60, HX, -10, LEAN, 10)
				.kunci(10, RAX, -90, HX, 0, LEAN, 4)
				.kunciE(14, Easing.BACK_OUT, RAX, -62, RAY, 6, HX, -10, LEAN, 10)
				.kunci(20, RAX, -90, RAY, -10, HX, 0, LEAN, 4)
				.wajahPada(4, -1, Wajah.MULUT_O).wajahPada(14, -1, Wajah.MULUT_A));
		d(b(HUAQUAN_B, 20).loop().bendera(BICARA | SINKRON).wajah(Wajah.MATA_KILAT, Wajah.MULUT_A)
				.kunci(0, RAX, -90, RAY, -10, LAX, -40, LEAN, 4, HX, 0)
				.kunciE(4, Easing.BACK_OUT, RAX, -64, RAY, 8, HX, -10, LEAN, 10)
				.kunci(10, RAX, -90, RAY, -10, HX, 0, LEAN, 4)
				.kunciE(14, Easing.BACK_OUT, RAX, -60, HX, -10, LEAN, 10)
				.kunci(20, RAX, -90, HX, 0, LEAN, 4)
				.wajahPada(4, -1, Wajah.MULUT_A).wajahPada(14, -1, Wajah.MULUT_TERTAWA));

		// 412: singing drunk with an arm round a neighbour, the cup swinging, swaying together
		d(b(NYANYI_MABUK, 40).loop().bendera(BICARA | SINKRON).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_TERTAWA)
				.kunci(0, LAX, -100, LAZ, -60, RAX, -90, RAY, -20, HX, -10)
				.osc(TWIST, 15.0F, 40.0F).osc(RAX, 20.0F, 20.0F).osc(HZ, 8.0F, 40.0F).osc(TILT, 4.0F, 40.0F)
				.partikel(10, "minecraft:note", 1, 0)
				.wajahPada(20, -1, Wajah.MULUT_O).wajahPada(30, -1, Wajah.MULUT_TERTAWA)
				.prop(PROP_CAWAN_ARAK, 0, 0));

		// 413: the shadow-play master behind the screen: two puppet rods raised, working them in turn
		d(b(DALANG, 40).loop().bendera(LIRIK_ATAS | BICARA).wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, RAX, -120, RAY, -30, LAX, -120, LAY, 30, HX, -14, LEAN, 4)
				.kunci(10, RAY, 0, LAY, 30).kunci(20, RAY, -30, LAY, 0).kunci(30, RAY, -30, LAY, 30)
				.osc(RAZ, 10.0F, 13.0F).osc(LAZ, -10.0F, 17.0F).osc(TWIST, 6.0F, 40.0F)
				.prop(PROP_WAYANG_TONGKAT, PROP_WAYANG_TONGKAT, 0));

		// 414: scrubbing soot off a wall with a cloth (K7)
		d(b(GOSOK_DINDING, 20).loop().bendera(KERJA).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, RAX, -70, RAY, -30, LAX, -40, LEAN, 10, HX, 6)
				.kunci(10, RAY, 30).kunci(20, RAY, -30)
				.osc(RAZ, 6.0F, 10.0F).osc(TWIST, 6.0F, 20.0F)
				.partikel(10, "debu", 1, 0)
				.prop(PropId.KAIN, 0, 0));

		// 415: ladling porridge into a held-out bowl
		d(b(BAGI_BUBUR, 40).loop().bendera(KERJA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, LAX, -50, LAY, 20, RAX, -70, RAY, -10, LEAN, 8, HX, 20)
				.kunciE(12, Easing.CUBIC_OUT, RAX, -90, RAY, 10, LEAN, 12)
				.kunci(20, RAX, -80, RAY, 18, RAZ, 14)
				.kunci(32, RAX, -70, RAY, -10, RAZ, 0, LEAN, 8)
				.partikel(14, "uap", 1, 0)
				.prop(PROP_KENDI_SUP, PROP_MANGKUK_BUBUR, 0));

		// 416: weighing on the steelyard: lifted up before the eyes, the weight slid along, the beam read
		d(b(MENIMBANG, 40).loop().bendera(LIRIK_ATAS).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -120, RAY, -10, LAX, -90, LAY, 20, HX, -20, LEAN, -2)
				.kunci(14, LAX, -96, LAY, 30).kunci(26, LAX, -90, LAY, 12).kunci(40, LAX, -90, LAY, 20)
				.osc(RAZ, 2.0F, 20.0F)
				.wajahPada(26, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(34, Wajah.MATA_SETENGAH, Wajah.MULUT_A)
				.prop(PROP_DACING, 0, 0));

		// 417: a witness testifying: talking with the hands, then pointing at the other party at t20
		d(b(BERSAKSI, 40).loop().bendera(BICARA | ARAH_SASARAN).wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, RAX, -40, RAY, -14, RAZ, 8, LAX, -30, HX, 0)
				.kunci(10, RAX, -60, RAZ, 14)
				.kunciE(20, Easing.CUBIC_OUT, RAX, -90, RAY, 0, RAZ, 0, HX, -4)
				.kunci(28, RAX, -90)
				.kunci(36, RAX, -40, RAY, -14, RAZ, 8, HX, 0)
				.osc(LAX, 10.0F, 20.0F).osc(HY, 5.0F, 40.0F)
				.wajahPada(20, Wajah.MATA_MARAH, -1).wajahPada(32, Wajah.MATA_BUKA, -1));

		// 418: the verdict: the gavel block raised and brought down, the head dipped
		d(b(PALU_PUTUSAN, 30).sekali().wajah(Wajah.MATA_MARAH, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(RAX, -30)))
				.kunci(4, RAX, -120, HX, -10)
				.kunciE(8, Easing.BACK_OUT, RAX, -50, HX, 0)
				.kunci(24, RAX, -50, HX, -10)
				.kunci(30, p(NOL))
				.suara(8, "block.wood.hit", 0.6F, 0.85F)
				.prop(PropId.PALU_KAYU, 0, 0));

		// 419: slipping a bribe: a lean in, the envelope pushed forward low, eyes darting left and right
		d(b(SODOR_AMPLOP, 30).sekali().wajah(Wajah.MATA_LIRIK_KIRI, Wajah.MULUT_MENYERINGAI)
				.kunci(0, p(NOL))
				.kunciE(8, Easing.CUBIC_OUT, LEAN, 20, RAX, -60, RAY, -10, HY, -20)
				.kunci(14, HY, 20).kunci(20, HY, -10, RAX, -64)
				.kunci(24, LEAN, 10, RAX, -40)
				.kunci(30, p(NOL))
				.propPada(22, PROP_SLOT_KANAN, 0)
				.wajahPada(14, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(22, Wajah.MATA_SETENGAH, Wajah.MULUT_SENYUM)
				.prop(PROP_ANGPAO, 0, 0));
	}

	// ================================================================== 420-427: building and festivals

	private static void bangunDanFestival() {
		// 420: driving a lot stake: the mallet swung high in both hands and brought down on the stake before the feet
		d(b(PASANG_PATOK, 24).loop().bendera(KERJA | DUA_TANGAN).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, RAX, -140, LAX, -140, LEAN, -4, HX, 0)
				.kunciE(10, Easing.CUBIC_IN, RAX, -60, LAX, -60, LEAN, 20, HX, 20)
				.kunci(16, RAX, -66, LAX, -66, LEAN, 18)
				.kunci(24, RAX, -140, LAX, -140, LEAN, -4, HX, 0)
				.suara(10, "block.wood.hit", 0.4F, 0.8F).partikel(10, "tanah", 2, 0)
				.prop(PropId.PALU_KAYU, 0, 0)
				.pasang(PROP_PAPAN_PATOK, Klip.Titik.DEPAN_KAKI, 0, 0, -6, 0, 0, 0, 1.0F));

		// 421: carrying a heavy beam on the shoulder, the hand steadying it, a stoop under the weight
		d(b(PIKUL_BALOK, 30).loop().bendera(JALAN_OK).mods(MOD_GEMETAR, 0.3F).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, RAX, -160, RAZ, 22, LAX, 15, LEAN, 8, HZ, 6, BAHU, -0.4F)
				.osc(LIFT, 0.02F, 15.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF)
				.pasang(PROP_BALOK_PIKUL, Klip.Titik.PUNGGUNG, -4.5F, -5.6F, -2.7F, 0, 0, 0, 1.0F));

		// 422: laying roof tiles from the scaffold: bent over, a tile taken from the stack and set in place
		d(b(SUSUN_GENTENG, 20).loop().bendera(KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, LEAN, 35, HX, 20, RAX, -80, RAY, -10, LAX, -50, LAY, 20)
				.kunciE(10, Easing.CUBIC_OUT, RAX, -40, RAY, 10, LEAN, 40)
				.kunci(20, RAX, -80, RAY, -10, LEAN, 35)
				.suara(10, "block.stone.place", 0.15F, 1.3F)
				.prop(0, PROP_GENTENG_TUMPUK, 0));

		// 423: cutting the ribbon at an opening: the shears raised, snapped shut at t20, a cheer with both arms
		d(b(POTONG_PITA, 40).sekali().wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL, v(LAX, -40)))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -90, RAY, -10, LAX, -70, LEAN, 6)
				.kunciE(20, Easing.BACK_OUT, RAZ, 10, LEAN, 8)
				.kunciE(28, Easing.CUBIC_OUT, RAX, -150, RAZ, 30, LAX, -150, LAZ, -30, LEAN, -4, LIFT, 0.04F)
				.kunci(34, LIFT, 0)
				.kunci(40, p(NOL, v(LAX, -40)))
				.kait(20, K_KONFETI).suara(20, "entity.firework_rocket.twinkle", 0.2F, 1.4F).suara(28, "entity.villager.celebrate", 0.2F, 1.2F)
				.wajahPada(28, Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.prop(PROP_GUNTING_BESAR, PROP_PITA_MERAH, 0));

		// 424: sweeping a grave at Qingming: the short broom in both hands, bent low, careful strokes
		d(b(SAPU_MAKAM, 24).loop().bendera(KERJA | DUA_TANGAN | LIRIK_BAWAH).wajah(Wajah.MATA_SEDIH, Wajah.MULUT_DIAM)
				.kunci(0, LEAN, 30, RAX, -50, LAX, -40, TWIST, -10, HX, 20)
				.kunci(12, TWIST, 10)
				.kunci(24, TWIST, -10)
				.partikel(6, "debu", 1, 0).partikel(18, "debu", 1, 0)
				.pasang(PROP_SAPU_LIDI, Klip.Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F));

		// 425: burning spirit money: squatting at the burner, feeding the sheets in one by one, smoke rising
		d(b(BAKAR_UANG_KERTAS, 60).sekali().bendera(KAKI_PAKSA | LIRIK_BAWAH).wajah(Wajah.MATA_SEDIH, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(RLX, 0, LLX, 0, LAX, -40)))
				.kunciE(10, Easing.CUBIC_OUT, p(JONGKOK, v(LEAN, 30, HX, 24, RAX, -40, LAX, -50)))
				.kunci(18, RAX, -20).kunci(24, RAX, -40).kunci(32, RAX, -20).kunci(38, RAX, -40).kunci(46, RAX, -20)
				.kunci(52, RAX, -30, LEAN, 30)
				.kunci(60, p(NOL, v(RLX, 0, LLX, 0, RLZ, 0, LLZ, 0, LAX, -40)))
				.kait(18, K_BAKAR).kait(32, K_BAKAR).kait(46, K_BAKAR)
				.suara(18, "block.campfire.crackle", 0.25F, 1.1F).suara(32, "block.campfire.crackle", 0.25F, 1.0F)
				.prop(0, PROP_UANG_KERTAS, 0));

		// 426: tucking a willow sprig above the door
		d(b(TANCAP_DEDALU, 20).sekali().bendera(LIRIK_ATAS).wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, p(NOL, v(RAX, -40)))
				.kunciE(8, Easing.CUBIC_OUT, RAX, -140, HX, -24, LIFT, 0.04F)
				.kunci(13, RAX, -144, RAY, -10)
				.kunci(20, p(NOL))
				.propPada(14, PROP_SLOT_KANAN, 0)
				.prop(PROP_RANTING_DEDALU, 0, 0));

		// 427: pasting a New Year couplet: both hands up holding it to the door post, a smoothing stroke at t25
		d(b(TEMPEL_KUPLET, 40).sekali().bendera(LIRIK_ATAS).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL, v(RAX, -40)))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -150, RAY, -10, LAX, -150, LAY, 10, HX, -20)
				.kunci(22, RAX, -152, LAX, -146)
				.kunciE(25, Easing.CUBIC_OUT, LAX, -120, LAY, 20).kunci(30, LAX, -150, LAY, 10)
				.kunci(40, p(NOL))
				.propPada(30, PROP_SLOT_KANAN, 0)
				.suara(25, "block.wool.place", 0.1F, 1.5F)
				.prop(PROP_KUPLET_MERAH, 0, 0));
	}

	// ================================================================== 428-449: children, villages, animals, seasons, sickness

	private static void anakDesaMusim() {
		// 428: cuju: the leather ball kept up with the instep, a hop on every kick
		d(b(MAIN_CUJU, 20).loop().bendera(KAKI_PAKSA | LIRIK_BAWAH).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RLX, 0, LLX, 0, LIFT, 0, RAX, -30, RAZ, 20, LAX, -30, LAZ, -20, LEAN, 6)
				.kunciE(5, Easing.BACK_OUT, RLX, -60, LIFT, 0.05F, RAX, -10, LAX, -50)
				.kunci(10, RLX, 0, LIFT, 0, RAX, -30, LAX, -30)
				.kunciE(15, Easing.BACK_OUT, LLX, -50, LIFT, 0.05F, RAX, -50, LAX, -10)
				.kunci(20, LLX, 0, LIFT, 0, RAX, -30, LAX, -30)
				.suara(5, "block.wool.hit", 0.2F, 1.3F).suara(15, "block.wool.hit", 0.2F, 1.2F)
				.pasang(PROP_BOLA_CUJU, Klip.Titik.DEPAN_KAKI, 0, 6, -4, 0, 0, 0, 1.0F));

		// 429: nibbling candied haws off the skewer
		d(b(MAKAN_TANGHULU, 40).loop().bendera(JALAN_OK).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -80, RAY, -20, HX, 0)
				.kunciE(8, Easing.CUBIC_OUT, RAX, -95, HX, -5).kunci(12, RAX, -95)
				.kunci(18, RAX, -80, HX, 0)
				.kunci(30, RAX, -82).kunci(40, RAX, -80)
				.wajahPada(8, -1, Wajah.MULUT_MAKAN).wajahPada(20, Wajah.MATA_TUTUP_TENANG, Wajah.MULUT_SENYUM)
				.wajahPada(30, Wajah.MATA_SENANG, -1)
				.prop(PROP_TANGHULU, 0, 0));

		// 430: a woman carrying a water jar on her head: one hand steadying it, a soft gliding step
		d(b(PIKUL_TEMPAYAN, 30).loop().bendera(JALAN_OK).wajah(Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				.kunci(0, LAX, -170, LAZ, -14, RAX, 6, RAZ, 6, HX, -2, LEAN, -2)
				.osc(BZ, 3.0F, 30.0F).osc(LIFT, 0.01F, 15.0F).osc(LAZ, 2.0F, 30.0F)
				.pasang(PROP_TEMPAYAN_AIR, Klip.Titik.KEPALA, 0, 0, 0, 0, 0, 0, 1.0F));

		// 431: casting the net: swung back with both arms, thrown out wide (t20, easeOutBack), hauled in hand over hand
		d(b(MENJALA, 60).sekali().bendera(KAKI_PAKSA).wajah(Wajah.MATA_BUKA, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, p(NOL, v(RLX, -10, LLX, 10)))
				.kunciE(12, Easing.CUBIC_OUT, RAX, -150, RAY, 20, LAX, -150, LAY, -20, TWIST, -30, LEAN, -6)
				.kunciE(20, Easing.BACK_OUT, RAX, -60, RAY, 0, LAX, -60, LAY, 0, TWIST, 20, LEAN, 16)
				.kunci(26, RAX, -70, LAX, -70, TWIST, 0, LEAN, 10)
				.kunci(40, RAX, -90, LAX, -50).kunci(45, RAX, -50, LAX, -90).kunci(50, RAX, -90, LAX, -50).kunci(55, RAX, -50, LAX, -90)
				.kunci(60, p(NOL, v(RLX, 0, LLX, 0)))
				.propPada(20, PROP_SLOT_KANAN, 0).propPada(44, PROP_SLOT_KANAN, PROP_JARING_IKAN)
				.kait(26, K_JALA).suara(20, "entity.fishing_bobber.throw", 0.3F, 0.7F).suara(26, "entity.generic.splash", 0.3F, 0.9F)
				.prop(PROP_JARING_IKAN, 0, 0));

		// 432: walking a grandchild by the hand: the elder's stoop and stick, the free hand lowered to the child's; the
		// child (variant SY_ANAK) reaches up to hold it, skipping
		d(b(GANDENG_CUCU, 50).loop().bendera(JALAN_OK | SINKRON).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, LEAN, 16, HX, -6, RAX, -35, RAZ, 10, LAX, -30, LAZ, -20, HY, -14)
				.osc(HY, 6.0F, 50.0F).osc(LAX, 4.0F, 25.0F)
				.prop(PropId.TONGKAT, 0, 0)
				.varian(SY_ANAK, v -> v.tanpaProp().wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
						.kunci(0, RAX, -80, RAZ, 20, LAX, 10, HY, 20, HX, -10)
						.osc(BOB, 0.5F, 12.5F, 0.0F, Osilator.Bentuk.SIN_POSITIF).osc(LAX, 20.0F, 25.0F)));

		// 433: crouching to stroke an animal, three slow strokes
		d(b(ELUS_HEWAN, 40).sekali().bendera(KAKI_PAKSA | LIRIK_BAWAH).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL, v(RLX, 0, LLX, 0)))
				.kunciE(8, Easing.CUBIC_OUT, p(JONGKOK, v(LEAN, 40, HX, 30, RAX, -30, RAY, -10)))
				.kunci(14, RAX, -20, RAY, 10).kunci(20, RAX, -30, RAY, -10).kunci(26, RAX, -20, RAY, 10).kunci(32, RAX, -30, RAY, -10)
				.kunci(40, p(NOL, v(RLX, 0, LLX, 0, RLZ, 0, LLZ, 0))));

		// 434: chasing a loose chicken: running bent over, arms out grabbing
		d(b(KEJAR_AYAM, 12).loop().bendera(JALAN_OK).wajah(Wajah.MATA_KAGET, Wajah.MULUT_A)
				.kunci(0, LEAN, 30, RAX, -70, RAZ, 30, LAX, -70, LAZ, -30, HX, 10)
				.osc(RAX, 15.0F, 6.0F).osc(LAX, -15.0F, 6.0F).osc(HY, 10.0F, 12.0F)
				.wajahPada(6, -1, Wajah.MULUT_O));

		// 435: gazing at the full moon on the festival night, seated, a mooncake in hand
		d(b(TATAP_BULAN, 80).loop().bendera(DUDUK | KAKI_PAKSA | LIRIK_ATAS).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_SENYUM)
				.kunci(0, p(DUDUK_LESEHAN, v(HX, -40, LAX, -40, LAZ, 10, RAX, -60, RAY, -20, LEAN, -6)))
				.kunci(50, RAX, -60).kunciE(56, Easing.CUBIC_OUT, RAX, -95, HX, -20).kunci(62, RAX, -95).kunci(70, RAX, -60, HX, -40)
				.osc(HY, 5.0F, 80.0F)
				.wajahPada(56, -1, Wajah.MULUT_MAKAN).wajahPada(66, -1, Wajah.MULUT_SENYUM)
				.prop(PROP_KUE_BULAN, 0, 0));

		// 436: a child parading the rabbit lantern held high, a merry skip
		d(b(ANGKAT_LENTERA_KELINCI, 40).loop().bendera(JALAN_OK | LIRIK_ATAS).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, -140, RAZ, 10, LAX, -20, LAZ, -14, HX, -20)
				.osc(RAZ, 6.0F, 40.0F).osc(BOB, 0.5F, 10.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).osc(LAX, 14.0F, 20.0F)
				.prop(PROP_LENTERA_KELINCI, 0, 0));

		// 437: giving a red envelope at New Year: offered in both hands, a small bow
		d(b(BERI_ANGPAO, 30).sekali().bendera(BICARA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL))
				.kunciE(8, Easing.CUBIC_OUT, RAX, -60, RAY, -16, LAX, -60, LAY, 16, LEAN, 14, HX, 16)
				.kunci(18, LEAN, 16)
				.kunci(24, LEAN, 6, RAX, -30, LAX, -30)
				.kunci(30, p(NOL))
				.propPada(18, PROP_SLOT_KANAN, 0)
				.prop(PROP_ANGPAO, 0, 0));

		// 438: a child receiving it: a little bow, the envelope in hand, a jump for joy
		d(b(TERIMA_ANGPAO, 30).sekali().wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL))
				.kunciE(6, Easing.CUBIC_OUT, p(BAOQUAN, v(LEAN, 30, HX, 20)))
				.kunci(12, LEAN, 0, HX, 0)
				.kunciE(18, Easing.CUBIC_OUT, RAX, -160, RAZ, 20, LAX, -40, LIFT, 0.18F)
				.kunciE(22, Easing.CUBIC_IN, LIFT, 0)
				.kunci(30, p(NOL))
				.propPada(10, PROP_SLOT_KANAN, PROP_ANGPAO)
				.suara(22, "block.grass.step", 0.15F, 1.5F)
				.wajahPada(16, Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA));

		// 439: waiting in line: weight shifting from foot to foot, craning to see the front, a sack in hand; variant:
		// empty-handed, arms folded
		java.util.function.Consumer<Klip.Pembangun> menunggu = k -> k
				.kunci(20, HY, 0).kunciE(26, Easing.CUBIC_OUT, HY, 12, HX, -6).kunci(34, HY, 12).kunci(40, HY, 0, HX, 0)
				.kunci(30, RLZ, 0, LLZ, -6, BZ, -2).kunci(60, RLZ, 6, LLZ, 0, BZ, 2)
				.wajahPada(28, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(42, Wajah.MATA_SETENGAH, -1);
		Klip.Pembangun antre = b(ANTRE, 60).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, RLZ, 6, LLZ, 0, BZ, 2, RAX, -10, LAX, -6, HY, 0, HX, 0)
				.prop(PROP_KARUNG_BERAS, 0, 0);
		menunggu.accept(antre);
		d(antre.varian(V_ANTRE_KOSONG, v -> {
			v.tanpaProp().kunci(0, RLZ, 6, LLZ, 0, BZ, 2, RAX, -60, RAY, 30, RAZ, 20, LAX, -60, LAY, -30, LAZ, -20, HY, 0, HX, 0);
			menunggu.accept(v);
		}));

		// 440: flying the dragon kite at Qingming: the line taut overhead, stepping back, tugging
		d(b(LAYANG_NAGA, 40).loop().bendera(LIRIK_ATAS | KAKI_PAKSA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -130, RAY, -6, LAX, -100, LAY, 14, HX, -34, LEAN, -10, RLX, 14, LLX, -10)
				.kunci(20, RAX, -120, RLX, -10, LLX, 14).kunci(40, RAX, -130, RLX, 14, LLX, -10)
				.osc(RAY, 8.0F, 40.0F).osc(RAX, 5.0F, 10.0F)
				.pasang(PROP_LAYANG_NAGA, Klip.Titik.KANAN, 0, -22.6F, -40.8F, -60, 0, 0, 1.4F));

		// 441: taking the hens to market in the bamboo basket, held out from the body
		d(b(DORONG_SANGKAR_AYAM, 30).loop().bendera(JALAN_OK).wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, RAX, -20, RAZ, 18, LAX, -14, LAZ, -10, LEAN, 4, TILT, -4)
				.osc(RAZ, 3.0F, 15.0F).osc(TILT, 2.0F, 30.0F)
				.prop(PROP_SANGKAR_AYAM, 0, 0));

		// 442: the tea-house waiter with the big tray in both hands, weaving between tables
		d(b(SAJIKAN_TEH_BESAR, 30).loop().bendera(JALAN_OK | DUA_TANGAN).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -70, RAY, -18, LAX, -70, LAY, 18, LEAN, 2, HX, 4)
				.osc(TWIST, 6.0F, 30.0F).osc(BZ, 2.0F, 15.0F)
				.pasang(PROP_NAMPAN_TEH_BESAR, Klip.Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F));

		// 443: an apprentice copying the master: the master's work stroke a beat late and smaller, eyes flicking to him
		d(b(TIRU, 40).loop().bendera(KERJA).wajah(Wajah.MATA_LIRIK_KIRI, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, RAX, -60, LAX, -40, LEAN, 12, HX, 20, HY, -20)
				.kunci(10, HY, -20).kunciE(14, Easing.CUBIC_OUT, HY, 0)
				.kunciE(22, Easing.CUBIC_IN, RAX, -30).kunci(30, RAX, -60).kunci(36, HY, -20)
				.osc(RAY, 6.0F, 40.0F)
				.wajahPada(14, Wajah.MATA_SETENGAH, -1).wajahPada(34, Wajah.MATA_LIRIK_KIRI, -1));

		// 444: feverish sleep: lying down, the head turning slowly, mumbling
		d(b(MENGIGAU, 80).loop().bendera(BERBARING | MATA_TUTUP | KAKI_PAKSA | BICARA).mods(MOD_GEMETAR, 0.4F)
				.wajah(Wajah.MATA_TUTUP, Wajah.MULUT_SEDIH)
				.kunci(0, RAX, -8, LAX, -8, RAZ, 6, LAZ, -6, HX, -6, HZ, -14)
				.kunci(40, HZ, 14).kunci(80, HZ, -14)
				.osc(LIFT, 0.01F, 40.0F)
				.suara(20, "entity.player.breath", 0.08F, 0.7F).suara(60, "entity.player.breath", 0.08F, 0.75F)
				.wajahPada(30, -1, Wajah.MULUT_A).wajahPada(36, -1, Wajah.MULUT_SEDIH));

		// 445: a cough: doubled over twice, a hand to the mouth
		d(b(BATUK, 20).sekali().wajah(Wajah.MATA_TUTUP, Wajah.MULUT_O)
				.kunci(0, p(NOL))
				.kunciE(3, Easing.CUBIC_OUT, RAX, -110, RAY, -20, LEAN, 20, HX, 14)
				.kunci(6, LEAN, 5).kunciE(9, Easing.CUBIC_OUT, LEAN, 20).kunci(13, LEAN, 5)
				.kunci(20, p(NOL))
				.kait(3, K_BATUK).kait(9, K_BATUK)
				.suara(3, "entity.player.hurt", 0.06F, 1.6F).suara(9, "entity.player.hurt", 0.06F, 1.7F));

		// 446: limping on a stick, the right leg favoured
		d(b(PINCANG, 30).loop().bendera(JALAN_OK).wajah(Wajah.MATA_SAKIT, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, LEAN, 14, HX, -6, RAX, -35, RAZ, 10, LAX, -6, TILT, 0)
				.kunci(8, TILT, 8, LIFT, -0.05F).kunci(15, TILT, 0, LIFT, 0).kunci(30, TILT, 0)
				.osc(BZ, 3.0F, 30.0F)
				.prop(PropId.TONGKAT, 0, 0));

		// 447: a child squatting to tease a cat with a dangling string
		d(b(MAIN_KUCING, 30).loop().bendera(KAKI_PAKSA | LIRIK_BAWAH).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, p(JONGKOK, v(LEAN, 30, HX, 26, RAX, -60, RAY, -10, LAX, -20)))
				.osc(RAY, 20.0F, 15.0F).osc(RAZ, 10.0F, 10.0F).osc(HY, 8.0F, 30.0F)
				.wajahPada(15, Wajah.MATA_KAGET, -1).wajahPada(22, Wajah.MATA_SENANG, -1));

		// 448: the morning stretch: arms up, a bend to the left, a bend to the right, a last reach
		d(b(PEREGANGAN_PAGI, 60).sekali().wajah(Wajah.MATA_TUTUP_TENANG, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(TILT, 0)))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -170, RAZ, 10, LAX, -170, LAZ, -10, HX, -14)
				.kunci(22, TILT, -16, RAZ, -10, LAZ, -26)
				.kunci(34, TILT, 16, RAZ, 26, LAZ, 10)
				.kunci(44, TILT, 0, RAZ, 10, LAZ, -10, LIFT, 0.03F)
				.kunci(52, RAX, -40, LAX, -40, LIFT, 0)
				.kunci(60, p(NOL, v(TILT, 0)))
				.suara(10, "entity.player.breath", 0.12F, 0.9F));

		// 449: gongxi: fist in palm, shaken twice before the chest with a small bow (New Year, an opening)
		d(b(UCAP_SELAMAT, 30).sekali().bendera(BICARA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(NOL))
				.kunciE(5, Easing.CUBIC_OUT, BAOQUAN)
				.kunci(10, RAX, -88, LAX, -85, LEAN, 10).kunci(14, RAX, -72, LAX, -70, LEAN, 6)
				.kunci(18, RAX, -88, LAX, -85, LEAN, 10).kunci(22, RAX, -72, LAX, -70, LEAN, 6)
				.kunci(30, p(NOL))
				.wajahPada(10, -1, Wajah.MULUT_TERTAWA));
	}

	// ================================================================== 450-459: 42.4A.7 micro breaks and spread activities

	private static void ragam() {
		// 450: wiping the sweat from the brow with the back of the hand, a breath out
		d(b(SEKA_KERINGAT, 30).sekali().wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_O)
				.kunci(0, p(NOL))
				.kunciE(8, Easing.CUBIC_OUT, RAX, -150, RAY, -30, HX, -10)
				.kunciE(18, Easing.SINE_IO, RAY, 20)
				.kunci(22, RAX, -140)
				.kunci(30, p(NOL))
				.kait(10, K_KERINGAT).suara(20, "entity.player.breath", 0.1F, 1.1F));

		// 451: scratching the head, puzzled
		d(b(GARUK_KEPALA, 24).sekali().wajah(Wajah.MATA_LIRIK_ATAS, Wajah.MULUT_CEMBERUT)
				.kunci(0, p(NOL))
				.kunciE(6, Easing.CUBIC_OUT, RAX, -160, RAY, -20, HX, 10, HZ, 6)
				.kunci(8, RAZ, 8).kunci(10, RAZ, -8).kunci(12, RAZ, 8).kunci(14, RAZ, -8).kunci(16, RAZ, 0)
				.kunci(24, p(NOL, v(HZ, 0))));

		// 452: settling the hat with both hands on the brim
		d(b(BETULKAN_TOPI, 20).sekali().wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, p(NOL))
				.kunciE(6, Easing.CUBIC_OUT, RAX, -165, RAY, -20, LAX, -165, LAY, 20, HX, 6)
				.kunci(10, HX, 10).kunci(13, HX, 4)
				.kunci(20, p(NOL)));

		// 453: looking round slowly, the body turning a little with the head
		d(b(LIHAT_SEKELILING, 40).sekali().wajah(Wajah.MATA_BUKA, -1)
				.kunci(0, p(NOL))
				.kunci(12, HY, -60, TWIST, -15)
				.kunci(28, HY, 60, TWIST, 15)
				.kunci(40, p(NOL))
				.wajahPada(12, Wajah.MATA_LIRIK_KIRI, -1).wajahPada(28, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(38, Wajah.MATA_BUKA, -1));

		// 454: weeding: squatting low, tugging weeds out and tossing them aside
		d(b(MENYIANGI, 30).loop().bendera(KAKI_PAKSA | KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, p(JONGKOK, v(LEAN, 55, HX, 30, RAX, -40, RAY, -6, LAX, -30)))
				.kunciE(10, Easing.CUBIC_OUT, RAX, -10, RAY, 10)
				.kunciE(16, Easing.BACK_OUT, RAX, -70, RAY, 40)
				.kunci(22, RAX, -40, RAY, -6)
				.kunci(30, RAX, -40)
				.partikel(10, "tanah", 1, 0).suara(10, "block.grass.step", 0.12F, 1.2F));

		// 455: shooing a fly away from the face
		d(b(USIR_LALAT, 16).sekali().wajah(Wajah.MATA_MARAH, Wajah.MULUT_CEMBERUT)
				.kunci(0, p(NOL))
				.kunciE(4, Easing.CUBIC_OUT, RAX, -90, RAZ, 40, HY, -10)
				.kunci(7, RAZ, -40).kunci(10, RAZ, 40).kunci(12, RAZ, -10)
				.kunci(16, p(NOL))
				.kait(4, K_LALAT));

		// 456: mending a net seated on the ground, both hands weaving the cord
		d(b(TAMBAL_JALA, 30).loop().bendera(DUDUK | KAKI_PAKSA | KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, p(DUDUK_LESEHAN, v(RAX, -60, RAY, -15, LAX, -60, LAY, 15, HX, 30, LEAN, 14)))
				.kunci(8, RAY, 15, LAY, -15).kunci(15, RAY, -15, LAY, 15).kunci(23, RAY, 15, LAY, -15).kunci(30, RAY, -15, LAY, 15)
				.osc(RAZ, 5.0F, 7.5F)
				.pasang(PROP_JARING_IKAN, Klip.Titik.PANGKUAN, 0, 0, 0, 0, 0, 0, 1.0F));

		// 457: hiding behind a barrel (hide-and-seek): crouched low, peeking out to either side
		d(b(SEMBUNYI_TONG, 20).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_KAGET, Wajah.MULUT_TERTAWA)
				.kunci(0, p(JONGKOK, v(LEAN, 30, RAX, -60, LAX, -60, HX, 0, HY, 0)))
				.kunciE(5, Easing.CUBIC_OUT, HY, 40, TILT, 10).kunci(9, HY, 0, TILT, 0)
				.kunciE(14, Easing.CUBIC_OUT, HY, -40, TILT, -10).kunci(18, HY, 0, TILT, 0)
				.wajahPada(5, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(14, Wajah.MATA_LIRIK_KIRI, -1));

		// 458: beating the dust off the clothes, hands alternating
		d(b(TEPUK_DEBU, 20).sekali().bendera(LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL))
				.kunci(3, RAX, -30, RAY, -20, LEAN, 8, HX, 16).kunciE(6, Easing.CUBIC_IN, RAX, -10)
				.kunci(9, LAX, -30, LAY, 20).kunciE(12, Easing.CUBIC_IN, LAX, -10)
				.kunci(14, RAX, -30).kunciE(16, Easing.CUBIC_IN, RAX, -10)
				.kunci(20, p(NOL))
				.kait(6, K_DEBU_BAJU).kait(12, K_DEBU_BAJU).kait(16, K_DEBU_BAJU)
				.suara(6, "block.wool.hit", 0.08F, 1.4F).suara(12, "block.wool.hit", 0.08F, 1.4F));

		// 459: a picnic on the grass: seated, eating from a lunch box on the lap, chatting between bites
		d(b(PIKNIK_DUDUK, 80).loop().bendera(DUDUK | KAKI_PAKSA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, p(DUDUK_LESEHAN, v(LAX, -50, LAY, 20, RAX, -50, RAY, -10, HX, 20, LEAN, 6)))
				.kunciE(14, Easing.CUBIC_OUT, RAX, -100, RAY, -20, HX, -4).kunci(20, RAX, -100)
				.kunci(28, RAX, -50, RAY, -10, HX, 20)
				.kunci(50, HY, 20, HX, 4).kunci(64, HY, -10).kunci(76, HY, 0, HX, 20)
				.wajahPada(16, -1, Wajah.MULUT_MAKAN).wajahPada(30, -1, Wajah.MULUT_SENYUM).wajahPada(52, -1, Wajah.MULUT_TERTAWA)
				.wajahPada(66, -1, Wajah.MULUT_SENYUM)
				.prop(PropId.SUMPIT, 0, 0)
				.pasang(PROP_KANTONG_KUE, Klip.Titik.PANGKUAN, 0, 0, 0, 0, 0, 0, 1.0F));

		// 460: chopping on the board: the knife lifted and brought down twice per loop, the left hand steadying, eyes on the cut
		d(b(POTONG_TALENAN, 16).loop().bendera(KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, LEAN, 18, HX, 28, RAX, -110, RAY, -6, LAX, -50, LAY, 12)
				.kunciE(4, Easing.CUBIC_IN, RAX, -50).kunci(8, RAX, -110).kunciE(12, Easing.CUBIC_IN, RAX, -50).kunci(16, RAX, -110)
				.suara(4, "block.wood.hit", 0.15F, 1.6F).suara(12, "block.wood.hit", 0.15F, 1.5F)
				.prop(PropId.PISAU_GANDA, 0, 0));

		// 461: kneading dough: both fists pressed down and away, folded back toward the body, a puff of flour
		d(b(ULENI_ADONAN, 30).loop().bendera(KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, LEAN, 28, HX, 24, RAX, -55, LAX, -55, RAZ, 6, LAZ, -6)
				.kunciE(8, Easing.CUBIC_OUT, LEAN, 36, RAX, -75, LAX, -75)
				.kunci(16, LEAN, 24, RAX, -45, LAX, -45, RAZ, 12, LAZ, -12)
				.kunci(24, RAZ, -6, LAZ, 6)
				.kunci(30, LEAN, 28, RAX, -55, LAX, -55, RAZ, 6, LAZ, -6)
				.partikel(8, "debu", 1, 0));

		// 462: sawing a plank: the blade pushed away and pulled back across the board, the left hand holding it down
		d(b(GERGAJI_KAYU, 24).loop().bendera(KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, LEAN, 14, HX, 26, RAX, -85, RAY, -8, LAX, -60, LAY, 14)
				.kunci(12, LEAN, 26, RAX, -40)
				.kunci(24, LEAN, 14, RAX, -85)
				.osc(TWIST, 3.0F, 24.0F)
				.suara(6, "block.wood.step", 0.08F, 0.7F).suara(18, "block.wood.step", 0.08F, 0.7F)
				.partikel(12, "debu", 1, 0)
				.prop(PropId.PAPAN_KAYU, 0, 0));
	}

	// ================================================================== effect hooks (NPC bodies)

	/**
	 * One {@code kota42:*} effect at body {@code e} facing {@code bodyRot} (degrees): a few particles placed for the pose
	 * of the clip that fires it. Never at Minimal quality or in a shader shadow pass; never changes the world.
	 */
	static void efek(String id, LivingEntity e, float bodyRot) {
		if (EffectQuality.minimal() || ShaderCompat.shadowPass() || !(e.level() instanceof ClientLevel level)) {
			return;
		}
		RandomSource r = level.getRandom();
		float yaw = bodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw);
		double fz = Mth.cos(yaw);
		// the body's right-hand side
		double kx = -Mth.cos(yaw);
		double kz = -Mth.sin(yaw);
		double x = e.getX();
		double y = e.getY();
		double z = e.getZ();
		double h = e.getBbHeight();
		switch (id) {
			case K_NYALA -> {
				// the taper at the pole's tip catching the lantern wick, about two blocks up and ahead
				double px = x + fx * 1.3D + kx * 0.3D;
				double py = y + h + 0.75D;
				double pz = z + fz * 1.3D + kz * 0.3D;
				int n = EffectQuality.scale(6);
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SPARK, 0xFFF4C0, 0xFFB040, 0xC04010, 0.05F, 0.01F,
							8 + r.nextInt(6), 0.0F, 0.0F, 0.0F, QiParticleOptions.GLOW), px + sebar(r, 0.1D), py + sebar(r, 0.1D),
							pz + sebar(r, 0.1D), sebar(r, 0.05D), 0.04D + r.nextDouble() * 0.04D, sebar(r, 0.05D));
				}
				level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0D, 0.01D, 0.0D);
			}
			case K_KERTAS -> {
				// spirit money flung up: yellow sheets fluttering and drifting down
				int n = EffectQuality.scale(8);
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_PETAL, 0xF0D890, 0xD8B04A, 0xB08A30, 0.12F, 0.1F,
							50 + r.nextInt(30), 0.0F, -0.01F, 0.0F, (byte) (QiParticleOptions.SPIN | QiParticleOptions.DRAG)),
							x + kx * 0.4D + fx * 0.3D, y + h + 0.3D, z + kz * 0.4D + fz * 0.3D,
							fx * 0.05D + sebar(r, 0.12D), 0.12D + r.nextDouble() * 0.1D, fz * 0.05D + sebar(r, 0.12D));
				}
			}
			case K_BAKAR -> {
				// the burner before the feet: a flare of embers, a gout of grey smoke, a flake of ash
				double px = x + fx * 0.7D;
				double pz = z + fz * 0.7D;
				int n = EffectQuality.scale(4);
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SPARK, 0xFFF0B0, 0xFF9030, 0xB03808, 0.05F, 0.015F,
							10 + r.nextInt(8), 0.0F, 0.0F, 0.0F, QiParticleOptions.GLOW), px + sebar(r, 0.15D), y + 0.35D,
							pz + sebar(r, 0.15D), sebar(r, 0.03D), 0.08D + r.nextDouble() * 0.06D, sebar(r, 0.03D));
				}
				level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xB0AAA0, 0x80786E, 0x4A4640, 0.18F, 0.5F,
						50 + r.nextInt(20), 0.0F, 0.035F, 0.0F, (byte) 0), px, y + 0.5D, pz, 0.0D, 0.04D, 0.0D);
				level.addParticle(ParticleTypes.WHITE_ASH, px + sebar(r, 0.2D), y + 0.8D, pz + sebar(r, 0.2D), 0.0D, 0.02D, 0.0D);
			}
			case K_AURA -> {
				// a root test answering: motes rising round the body from the stone to above the head
				int n = EffectQuality.scale(10);
				for (int i = 0; i < n; i++) {
					double a = r.nextDouble() * Mth.TWO_PI;
					double rad = 0.45D + r.nextDouble() * 0.25D;
					level.addParticle(new QiParticleOptions(0xFFFFFF, 0xB8F0FF, 0x5AA8D8, 0.08F, 0.02F, 24 + r.nextInt(12), 0.0F, 0.05F, 0.0F,
							(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.FADE_IN)),
							x + Math.cos(a) * rad, y + r.nextDouble() * 0.6D, z + Math.sin(a) * rad, 0.0D, 0.05D, 0.0D);
				}
			}
			case K_KONFETI -> {
				// the ribbon parted: red and gold paper confetti thrown up over the crowd
				int n = EffectQuality.scale(14);
				for (int i = 0; i < n; i++) {
					boolean emas = r.nextInt(3) == 0;
					level.addParticle(new QiParticleOptions(QiParticles.QI_PETAL, emas ? 0xFFE8A0 : 0xF04030, emas ? 0xE8B848 : 0xC02018,
							emas ? 0xA88020 : 0x801010, 0.08F, 0.07F, 40 + r.nextInt(30), 0.0F, -0.01F, 0.0F,
							(byte) (QiParticleOptions.SPIN | QiParticleOptions.DRAG)), x + fx * 0.6D, y + h * 0.7D, z + fz * 0.6D,
							sebar(r, 0.15D), 0.18D + r.nextDouble() * 0.12D, sebar(r, 0.15D));
				}
			}
			case K_KERINGAT -> {
				// two drops flicked from the brow
				for (int i = 0; i < 2; i++) {
					level.addParticle(ParticleTypes.FALLING_WATER, x + fx * 0.22D + kx * (0.08D * (i * 2 - 1)), y + h * 0.9D,
							z + fz * 0.22D + kz * (0.08D * (i * 2 - 1)), 0.0D, 0.0D, 0.0D);
				}
			}
			case K_GULA -> {
				// the molten sugar thread glinting where it meets the slab
				int n = Math.max(1, EffectQuality.scale(3));
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SPARK, 0xFFF0C0, 0xFFC860, 0xC8781A, 0.035F, 0.0F,
							10 + r.nextInt(6), 0.0F, 0.0F, 0.0F, (byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY)),
							x + fx * 0.75D + kx * sebar(r, 0.15D), y + h * 0.45D, z + fz * 0.75D + kz * sebar(r, 0.15D), 0.0D, 0.0D, 0.0D);
				}
			}
			case K_LALAT -> {
				// a couple of flies buzzing off from the face
				for (int i = 0; i < 2; i++) {
					level.addParticle(new QiParticleOptions(0x2A2620, 0x1E1A18, 0x141210, 0.025F, 0.025F, 20 + r.nextInt(10), 0.0F, 0.0F, 0.0F,
							(byte) (QiParticleOptions.NO_GRAVITY | QiParticleOptions.SPIN)), x + fx * 0.4D + sebar(r, 0.2D),
							y + h * 0.85D + sebar(r, 0.1D), z + fz * 0.4D + sebar(r, 0.2D), sebar(r, 0.08D), 0.02D, sebar(r, 0.08D));
				}
			}
			case K_DEBU_BAJU -> {
				// dust puffing out of the clothes at the hip
				int n = EffectQuality.scale(3);
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xC8BCA8, 0xA89C88, 0x7A705E, 0.08F, 0.2F,
							20 + r.nextInt(10), 0.0F, 0.0F, 0.0F, QiParticleOptions.DRAG), x + fx * 0.25D + sebar(r, 0.2D),
							y + h * 0.45D + sebar(r, 0.1D), z + fz * 0.25D + sebar(r, 0.2D), sebar(r, 0.03D), 0.01D, sebar(r, 0.03D));
				}
			}
			case K_JALA -> {
				// the cast net landing in a ring of splashes three blocks out
				double cx = x + fx * 3.0D;
				double cz = z + fz * 3.0D;
				int n = EffectQuality.scale(12);
				for (int i = 0; i < n; i++) {
					double a = i / (double) Math.max(1, n) * Mth.TWO_PI;
					level.addParticle(ParticleTypes.SPLASH, cx + Math.cos(a) * 1.0D, y + 0.1D, cz + Math.sin(a) * 1.0D, 0.0D, 0.1D, 0.0D);
				}
			}
			case K_BATUK -> level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xF0F0EC, 0xD8D8D4, 0xB0B0AC, 0.05F, 0.14F,
					12 + r.nextInt(6), 0.0F, 0.0F, 0.0F, (byte) (QiParticleOptions.NO_GRAVITY | QiParticleOptions.DRAG)),
					x + fx * 0.35D, y + h * 0.82D, z + fz * 0.35D, fx * 0.04D, 0.0D, fz * 0.04D);
			case K_AIR -> {
				// water thrown out of the bucket in an arc ahead
				int n = EffectQuality.scale(10);
				for (int i = 0; i < n; i++) {
					double s = 0.6D + r.nextDouble() * 1.4D;
					level.addParticle(ParticleTypes.SPLASH, x + fx * s + sebar(r, 0.25D), y + h * 0.6D - s * 0.25D, z + fz * s + sebar(r, 0.25D),
							fx * 0.1D, 0.05D, fz * 0.1D);
				}
				level.addParticle(ParticleTypes.CLOUD, x + fx * 1.6D, y + 0.4D, z + fz * 1.6D, 0.0D, 0.04D, 0.0D);
			}
			default -> {
			}
		}
	}

	private static double sebar(RandomSource r, double d) {
		return (r.nextDouble() - 0.5D) * 2.0D * d;
	}

	private KotaPoses() {
	}
}

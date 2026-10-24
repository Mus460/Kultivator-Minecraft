package dev.kultivator.client.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.client.sosok.gerak.Easing;
import dev.kultivator.client.sosok.gerak.GerakKatalog;
import dev.kultivator.client.sosok.gerak.Klip;
import dev.kultivator.client.sosok.gerak.PropKatalog;
import dev.kultivator.client.sosok.gerak.Wajah;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;

import static dev.kultivator.client.sosok.gerak.Klip.ARAH_SASARAN;
import static dev.kultivator.client.sosok.gerak.Klip.BAHU;
import static dev.kultivator.client.sosok.gerak.Klip.BICARA;
import static dev.kultivator.client.sosok.gerak.Klip.BOB;
import static dev.kultivator.client.sosok.gerak.Klip.BZ;
import static dev.kultivator.client.sosok.gerak.Klip.HX;
import static dev.kultivator.client.sosok.gerak.Klip.HY;
import static dev.kultivator.client.sosok.gerak.Klip.HZ;
import static dev.kultivator.client.sosok.gerak.Klip.JALAN_OK;
import static dev.kultivator.client.sosok.gerak.Klip.KAKI_PAKSA;
import static dev.kultivator.client.sosok.gerak.Klip.LAX;
import static dev.kultivator.client.sosok.gerak.Klip.LAY;
import static dev.kultivator.client.sosok.gerak.Klip.LAZ;
import static dev.kultivator.client.sosok.gerak.Klip.LEAN;
import static dev.kultivator.client.sosok.gerak.Klip.LIFT;
import static dev.kultivator.client.sosok.gerak.Klip.LLX;
import static dev.kultivator.client.sosok.gerak.Klip.LLZ;
import static dev.kultivator.client.sosok.gerak.Klip.MOD_DETAK;
import static dev.kultivator.client.sosok.gerak.Klip.MOD_GEMETAR;
import static dev.kultivator.client.sosok.gerak.Klip.NAPAS;
import static dev.kultivator.client.sosok.gerak.Klip.PROP_SLOT_KANAN;
import static dev.kultivator.client.sosok.gerak.Klip.RAX;
import static dev.kultivator.client.sosok.gerak.Klip.RAY;
import static dev.kultivator.client.sosok.gerak.Klip.RAZ;
import static dev.kultivator.client.sosok.gerak.Klip.RLX;
import static dev.kultivator.client.sosok.gerak.Klip.RLZ;
import static dev.kultivator.client.sosok.gerak.Klip.SY_P;
import static dev.kultivator.client.sosok.gerak.Klip.TWIST;

/**
 * The body language of feuds (DESIGN 39.F.5) on the shared Gerak system (40G, schedule matrix row 17): clips 250-273,
 * props 130-140 ({@code kultivator:fx/sekte_mandiri_seteru_<name>}) and the activity map of 57-60 ({@code CEKCOK},
 * {@code TANTANG}, {@code TERLUKA}, {@code SUMPAH}), replacing 40G's plain defaults. Tables write degrees (arm
 * {@code xRot < 0} raises forward, right arm {@code yRot < 0} swings inward, {@code LEAN > 0} bends forward, {@code LIFT}
 * in blocks, {@code BOB}/{@code BAHU} in pixels). Each clip is built once at client init; variants pick a woman's stance
 * ({@code SY_P}) or a proud head ({@code SOMBONG}). Client thread only.
 */
final class SeteruPoses {

	private static boolean siap;

	static void daftar() {
		if (siap) {
			return;
		}
		siap = true;
		props();
		cekcok();
		tantangDanDuel();
		lukaDanSumpah();
		jalanan();
		tetua();
		suasana();
		GerakKatalog.petakanAktivitas(MandiriKegiatan.ACT_CEKCOK, SeteruIds.KLIP_CEKCOK_BALAS, 0);
		GerakKatalog.petakanAktivitas(MandiriKegiatan.ACT_TANTANG, SeteruIds.KLIP_TANTANG, 0);
		GerakKatalog.petakanAktivitas(MandiriKegiatan.ACT_TERLUKA, SeteruIds.KLIP_TERLUKA, 0);
		GerakKatalog.petakanAktivitas(MandiriKegiatan.ACT_SUMPAH, SeteruIds.KLIP_SUMPAH, 0);
	}

	private static Klip.Pembangun b(int id, String nama, int durasi) {
		return Klip.baru(id, "seteru_" + nama, durasi);
	}

	// ================================================================== props 130-140

	private static void props() {
		for (int i = 0; i < SeteruIds.PROP_NAMA.length; i++) {
			int id = SeteruIds.PROP_PERBAN + i;
			String name = SeteruIds.PROP_NAMA[i];
			PropKatalog.Pegangan grip = switch (id) {
				case SeteruIds.PROP_PANJI_LURUS, SeteruIds.PROP_PANJI_NETRAL, SeteruIds.PROP_PANJI_IBLIS, SeteruIds.PROP_TIANG_PANJI ->
						new PropKatalog.Pegangan(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, PropKatalog.PANJANG | PropKatalog.BESAR);
				case SeteruIds.PROP_KRUK -> new PropKatalog.Pegangan(0.0F, 5.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, PropKatalog.PANJANG);
				case SeteruIds.PROP_GULUNGAN_CURIAN, SeteruIds.PROP_SURAT_TANTANGAN ->
						new PropKatalog.Pegangan(0.0F, 0.0F, -1.0F, 90.0F, 0.0F, 0.0F, 0.9F, 0);
				case SeteruIds.PROP_KANTONG_GANTI_RUGI -> new PropKatalog.Pegangan(0.0F, 2.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.9F, 0);
				case SeteruIds.PROP_CAWAN_PECAH -> new PropKatalog.Pegangan(0.0F, 0.0F, -0.5F, 90.0F, 0.0F, 0.0F, 0.9F, 0);
				case SeteruIds.PROP_TALI_MERAH -> new PropKatalog.Pegangan(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.9F, 0);
				default -> null;
			};
			PropKatalog.daftar(id, Kultivator.id("fx/sekte_mandiri_seteru_" + name), grip);
		}
	}

	// ================================================================== the quarrel (57)

	private static void cekcok() {
		// 250: the accuser. Chest out, head cocked, the right index finger stabs toward the other (ARAH_SASARAN), the left
		// fist on the hip; every few beats a sharper jab and a shoulder shrug of contempt
		GerakKatalog.daftar(b(SeteruIds.KLIP_CEKCOK, "cekcok", 40).loop().bendera(BICARA | ARAH_SASARAN)
				.wajah(Wajah.MATA_MARAH, Wajah.MULUT_MARAH)
				.kunci(0, LEAN, -6, HX, -8, HZ, 6, RAX, -84, RAY, -6, RAZ, 4, LAX, 8, LAY, -20, LAZ, -25, BAHU, 0.0F, TWIST, 4)
				.kunciE(6, Easing.CUBIC_OUT, RAX, -98, HX, -11, LEAN, -8, BAHU, 0.5F)
				.kunci(12, RAX, -84, HX, -8, BAHU, 0.0F)
				.kunci(20, LEAN, -5, HZ, 2, TWIST, -3)
				.kunciE(26, Easing.CUBIC_OUT, RAX, -102, RAZ, 8, HX, -12, BAHU, 0.6F)
				.kunci(32, RAX, -86, RAZ, 4, HX, -9, BAHU, 0.0F)
				.kunci(40, LEAN, -6, HX, -8, HZ, 6, RAX, -84, RAY, -6, RAZ, 4, TWIST, 4)
				.osc(HY, 5.0F, 40.0F).osc(BZ, 0.8F, 20.0F)
				.varian(SY_P, v -> v.durasi(40)
						// a woman's scolding: one hand on the hip, the other wagging the finger, a toss of the head
						.kunci(0, LEAN, -4, HX, -6, HZ, -8, RAX, -76, RAY, -14, RAZ, 6, LAX, 6, LAY, -18, LAZ, -28, BZ, 1.5F)
						.kunci(10, RAZ, 18, HZ, -4).kunci(20, RAZ, -4, HZ, -9).kunci(30, RAZ, 16, HZ, -5)
						.kunci(40, LEAN, -4, HX, -6, HZ, -8, RAX, -76, RAY, -14, RAZ, 6, BZ, 1.5F)
						.wajahPada(18, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(30, Wajah.MATA_MARAH, -1))
				.varian(Klip.sifat(Sifat.SOMBONG.ordinal()), v -> v.warisi().kunci(0, HX, -18).kunci(40, HX, -18)
						.wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_MENYERINGAI))
				.bangun());
		// 251: the answer (activity 57 of our disciple): arms folded, then flung open, palms out, then a finger back
		GerakKatalog.daftar(b(SeteruIds.KLIP_CEKCOK_BALAS, "cekcok_balas", 48).loop().bendera(BICARA | ARAH_SASARAN)
				.wajah(Wajah.MATA_MARAH, Wajah.MULUT_CEMBERUT)
				.kunci(0, RAX, -57, RAY, -44, RAZ, 3, LAX, -54, LAY, 44, LAZ, -3, HX, -6, LEAN, -2)
				.kunci(10, RAX, -57, RAY, -44, LAX, -54, LAY, 44)
				.kunciE(14, Easing.BACK_OUT, RAX, -62, RAY, 32, RAZ, 26, LAX, -62, LAY, -32, LAZ, -26, LEAN, -5, HX, -9)
				.kunci(24, RAX, -60, RAY, 30, RAZ, 24, LAX, -60, LAY, -30, LAZ, -24)
				.kunciE(28, Easing.CUBIC_OUT, RAX, -92, RAY, -4, RAZ, 4, LAX, 10, LAY, -16, LAZ, -20, HX, -10)
				.kunci(38, RAX, -88, HX, -8)
				.kunci(48, RAX, -57, RAY, -44, RAZ, 3, LAX, -54, LAY, 44, LAZ, -3, HX, -6, LEAN, -2)
				.wajahPada(14, -1, Wajah.MULUT_MARAH).wajahPada(38, -1, Wajah.MULUT_CEMBERUT)
				.osc(HY, 4.0F, 24.0F)
				.bangun());
		// 256: the sneer at the master's teaching: chin up, hands behind the back, then a flick of the hand
		GerakKatalog.daftar(b(SeteruIds.KLIP_EJEK_GURU, "ejek_guru", 44).sekali().wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_MENYERINGAI)
				.kunci(0, RAX, 28, RAZ, -12, LAX, 28, LAZ, 12, HX, -14, LEAN, -5)
				.kunci(10, RAX, 28, HX, -15)
				.kunciE(16, Easing.CUBIC_OUT, RAX, -62, RAY, 32, RAZ, 6, HY, -12)
				.kunci(24, RAX, -48, RAY, 12, HY, -6)
				.kunci(34, RAX, 28, RAY, 0, RAZ, -12, HY, 0)
				.kunci(44, RAX, 28, RAZ, -12, LAX, 28, LAZ, 12, HX, -14, LEAN, -5)
				.wajahPada(18, Wajah.MATA_LIRIK_KIRI, -1).wajahPada(34, Wajah.MATA_SETENGAH, -1)
				.bangun());
	}

	// ================================================================== challenge, duel outcome (58)

	private static void tantangDanDuel() {
		// 252: the fist-and-palm salute (baoquan): hands meet before the chest, a slight bow, eyes flash
		GerakKatalog.daftar(b(SeteruIds.KLIP_TANTANG, "tantang", 54).sekali().wajah(Wajah.MATA_KILAT, Wajah.MULUT_DIAM)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.kunciE(8, Easing.CUBIC_OUT, RAX, -82, RAY, -36, RAZ, 2, LAX, -80, LAY, 40, LAZ, -2, LEAN, 9, HX, 7)
				.kunci(12, LEAN, 11, HX, 9)
				.kunci(34, RAX, -82, RAY, -36, LAX, -80, LAY, 40, LEAN, 10, HX, 8)
				.kunciE(40, Easing.CUBIC_OUT, LEAN, -2, HX, -4, RAX, -20, RAY, -8, LAX, -18, LAY, 8)
				.kunci(54, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.suara(8, "block.wool.hit", 0.35F, 1.3F).partikel(9, "kilau", 3, 0xF2D27A)
				.varian(SY_P, v -> v.warisi().kunci(12, LEAN, 7, HX, 6).kunci(34, LEAN, 6, HX, 5))
				.bangun());
		// 263: the victor: the sword arm raised, the other hand behind the back, chin up
		GerakKatalog.daftar(b(SeteruIds.KLIP_MENANG, "menang", 60).sekali().wajah(Wajah.MATA_SENANG, Wajah.MULUT_MENYERINGAI)
				.kunci(0, RAX, 0, LAX, 0, HX, 0, LEAN, 0)
				.kunciE(10, Easing.BACK_OUT, RAX, -152, RAZ, 10, LAX, 28, LAZ, 12, HX, -12, LEAN, -4)
				.kunci(44, RAX, -148, RAZ, 8, HX, -10)
				.kunci(60, RAX, 0, RAZ, 0, LAX, 0, LAZ, 0, HX, 0, LEAN, 0)
				.partikel(12, "kilau", 4, 0xFFE9A8).suara(10, "entity.player.levelup", 0.15F, 1.6F)
				.bangun());
		// 264: the loser: down on one knee, a hand clutching the chest, blood at the lips; held until the next clip
		GerakKatalog.daftar(b(SeteruIds.KLIP_KALAH, "kalah", 60).tahan().bendera(KAKI_PAKSA).mods(MOD_GEMETAR, 0.4F)
				.wajah(Wajah.MATA_SAKIT, -1)
				.kunci(0, LEAN, 4, HX, 4, LAX, 0, RAX, 0, RLX, 0, LLX, 0, LIFT, 0)
				.kunciE(12, Easing.CUBIC_IN, LEAN, 22, HX, 20, LAX, -62, LAY, 42, LAZ, -6, RAX, -22, RAZ, 12, RLX, -88, LLX, 58, LIFT, -0.42F)
				.kunci(60, LEAN, 20, HX, 18, LAX, -60, LAY, 40, RAX, -20, RAZ, 10, RLX, -88, LLX, 58, LIFT, -0.42F)
				.wajahPada(12, -1, Wajah.MULUT_BATUK_DARAH).wajahPada(36, -1, Wajah.MULUT_MENGGIGIT_BIBIR)
				.partikel(12, "darah", 2, 0).suara(12, "entity.player.big_fall", 0.25F, 1.0F)
				.bangun());
	}

	// ================================================================== wounded (59) and the vow (60)

	private static void lukaDanSumpah() {
		// 253: limping home (the legs limp through Gaya.LUKA_PINCANG): the left hand clutches the ribs, the right leans on a
		// crutch, a bandage around the head, a faint tremor of pain
		GerakKatalog.daftar(b(SeteruIds.KLIP_TERLUKA, "terluka", 50).loop().lapisan(Klip.Lapisan.ATAS).bendera(JALAN_OK)
				.mods(MOD_GEMETAR, 0.25F).wajah(Wajah.MATA_SAKIT, Wajah.MULUT_MENGGIGIT_BIBIR)
				.prop(SeteruIds.PROP_KRUK, 0, 0)
				.pasang(SeteruIds.PROP_PERBAN, Klip.Titik.KEPALA, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F)
				.kunci(0, LEAN, 12, HX, 14, LAX, -48, LAY, 52, LAZ, -10, RAX, -12, RAZ, 6, NAPAS, 1.4F)
				.kunci(25, LEAN, 14, HX, 16, LAX, -52, LAY, 54, RAX, -16, RAZ, 7)
				.kunci(50, LEAN, 12, HX, 14, LAX, -48, LAY, 52, LAZ, -10, RAX, -12, RAZ, 6)
				.wajahPada(20, Wajah.MATA_TUTUP, -1).wajahPada(24, Wajah.MATA_SAKIT, -1)
				.bangun());
		// 254: the vow of revenge: drops to one knee, the right fist strikes the ground (dust), the head bowed, then it looks up
		// with burning eyes and swears; held while the activity lasts
		GerakKatalog.daftar(b(SeteruIds.KLIP_SUMPAH, "sumpah", 70).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MARAH)
				.kunci(0, LEAN, 0, HX, 0, RAX, 0, LAX, 0, RLX, 0, LLX, 0, LIFT, 0)
				.kunciE(8, Easing.CUBIC_IN, LEAN, 18, HX, 16, RLX, -88, LLX, 56, LIFT, -0.42F, RAX, -60, RAZ, 6, LAX, -46, LAZ, -6)
				.kunciE(13, Easing.CUBIC_IN, LEAN, 32, HX, 26, RAX, -18, RAZ, 4)
				.kunci(30, LEAN, 30, HX, 24, RAX, -18)
				.kunciE(38, Easing.CUBIC_OUT, LEAN, 14, HX, -12, RAX, -26)
				.kunci(70, LEAN, 14, HX, -10, RAX, -24, RAZ, 4, LAX, -46, LAZ, -6, RLX, -88, LLX, 56, LIFT, -0.42F)
				.suara(13, "entity.player.big_fall", 0.3F, 0.85F).partikel(13, "debu", 6, 0).partikel(13, "tanah", 4, 0)
				.wajahPada(13, Wajah.MATA_TUTUP, Wajah.MULUT_MENGGIGIT_BIBIR).wajahPada(38, Wajah.MATA_KILAT, Wajah.MULUT_A)
				.bangun());
	}

	// ================================================================== the street: patrols, brawls, lovers, thieves

	private static void jalanan() {
		// 255: a patrol walking the road: a hand on the hilt, chest out, the head scanning slowly (the banner on the back
		// comes from the server's prop)
		GerakKatalog.daftar(b(SeteruIds.KLIP_PATROLI, "patroli", 80).loop().lapisan(Klip.Lapisan.ATAS).bendera(JALAN_OK)
				.wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, LAX, -22, LAY, 18, LAZ, 9, LEAN, -3, HX, -5)
				.osc(HY, 22.0F, 80.0F).osc(RAX, 6.0F, 40.0F)
				.bangun());
		// 258: the brawl's cheering: fists pumped in turn, a bounce, mouths shouting
		GerakKatalog.daftar(b(SeteruIds.KLIP_SORAK_KEROYOK, "sorak", 30).loop().wajah(Wajah.MATA_MARAH, Wajah.MULUT_A)
				.kunci(0, RAX, -160, RAZ, 8, LAX, -40, LAZ, -8, BOB, 0, HX, -10)
				.kunciE(7, Easing.CUBIC_OUT, BOB, 0.8F)
				.kunci(15, RAX, -40, RAZ, 8, LAX, -160, LAZ, -8, BOB, 0)
				.kunciE(22, Easing.CUBIC_OUT, BOB, 0.8F)
				.kunci(30, RAX, -160, RAZ, 8, LAX, -40, LAZ, -8, BOB, 0, HX, -10)
				.osc(HY, 8.0F, 30.0F).mods(MOD_DETAK, 0.0F)
				.bangun());
		// 259: shielding a villager or a junior: the left arm thrown out to the side, the right fist ready, feet apart
		GerakKatalog.daftar(b(SeteruIds.KLIP_LINDUNGI, "lindungi", 40).loop().wajah(Wajah.MATA_MARAH, Wajah.MULUT_MARAH)
				.kunci(0, LAX, -82, LAY, -48, LAZ, -6, RAX, -62, RAY, -12, LEAN, -2, HX, -6, RLZ, 6, LLZ, -6)
				.osc(RAX, 6.0F, 20.0F).osc(BZ, 0.6F, 40.0F)
				.bangun());
		// 260: planting the sect banner on a road post as a challenge (a prop on the body, never a block)
		GerakKatalog.daftar(b(SeteruIds.KLIP_TANCAP_PANJI, "tancap_panji", 60).sekali().wajah(Wajah.MATA_KILAT, Wajah.MULUT_MENYERINGAI)
				.prop(SeteruIds.PROP_TIANG_PANJI, 0, 0)
				.kunci(0, RAX, -30, LAX, -30, LEAN, 0, HX, 0)
				.kunciE(14, Easing.CUBIC_OUT, RAX, -172, LAX, -168, LEAN, -10, HX, -20)
				.kunciE(22, Easing.CUBIC_IN, RAX, -44, LAX, -42, LEAN, 22, HX, 10)
				.kunci(40, RAX, -40, LAX, -40, LEAN, 18)
				.kunci(60, RAX, 0, LAX, 0, LEAN, 0, HX, 0)
				.suara(22, "block.wood.place", 0.6F, 0.8F).partikel(22, "debu", 5, 0).propPada(42, PROP_SLOT_KANAN, 0)
				.bangun());
		// 261: the secret meeting of forbidden lovers: a red string held to the heart, glances over the shoulder, a shy smile
		GerakKatalog.daftar(b(SeteruIds.KLIP_RAHASIA, "rahasia", 60).loop().wajah(Wajah.MATA_MALU, Wajah.MULUT_SENYUM)
				.prop(0, SeteruIds.PROP_TALI_MERAH, 0)
				.kunci(0, LAX, -42, LAY, 32, LAZ, -6, RAX, -8, LEAN, 4, HX, 6)
				.kunci(30, LAX, -46, LAY, 34, HX, 8)
				.kunci(60, LAX, -42, LAY, 32, LAZ, -6, RAX, -8, LEAN, 4, HX, 6)
				.osc(HY, 34.0F, 60.0F)
				.wajahPada(18, Wajah.MATA_LIRIK_KIRI, -1).wajahPada(26, Wajah.MATA_MALU, -1).wajahPada(48, Wajah.MATA_LIRIK_KANAN, -1)
				.bangun());
		// 267: the thief: a quick snatch, the scroll hidden behind the back, a look over the shoulder
		GerakKatalog.daftar(b(SeteruIds.KLIP_CURI, "curi", 40).sekali().wajah(Wajah.MATA_LIRIK_KIRI, Wajah.MULUT_MENYERINGAI)
				.kunci(0, RAX, -20, HY, 0, LEAN, 0)
				.kunciE(6, Easing.CUBIC_OUT, RAX, -92, LEAN, 12)
				.kunciE(14, Easing.CUBIC_OUT, RAX, 42, RAZ, -12, LEAN, -2)
				.kunci(24, HY, 38).kunci(34, HY, 30)
				.kunci(40, RAX, 40, RAZ, -12, HY, 0, LEAN, 0)
				.propPada(6, PROP_SLOT_KANAN, SeteruIds.PROP_GULUNGAN_CURIAN)
				.bangun());
	}

	// ================================================================== elders and endings

	private static void tetua() {
		// 262: an elder parts them: both palms thrust out between them, "Cukup!"
		GerakKatalog.daftar(b(SeteruIds.KLIP_TETUA_LERAI, "tetua_lerai", 64).sekali().wajah(Wajah.MATA_MARAH, Wajah.MULUT_A)
				.kunci(0, RAX, 0, LAX, 0, LEAN, 0, HX, 0)
				.kunciE(6, Easing.BACK_OUT, RAX, -108, RAY, -6, RAZ, 12, LAX, -102, LAY, 8, LAZ, -12, LEAN, -6, HX, -8)
				.kunci(40, RAX, -104, RAZ, 10, LAX, -100, LAZ, -10)
				.kunci(64, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, LAZ, 0, LEAN, 0, HX, 0)
				.partikel(6, "qi_tipis", 6, -1).suara(6, "block.amethyst_block.step", 0.4F, 0.7F)
				.wajahPada(30, -1, Wajah.MULUT_DIAM)
				.bangun());
		// 257: the apology: a deep bow over cupped fists
		GerakKatalog.daftar(b(SeteruIds.KLIP_MAAF, "maaf", 70).sekali().wajah(Wajah.MATA_TUTUP, Wajah.MULUT_DIAM)
				.kunci(0, LEAN, 0, HX, 0, RAX, 0, LAX, 0)
				.kunciE(12, Easing.SINE_IO, LEAN, 38, HX, 20, RAX, -70, RAY, -38, LAX, -70, LAY, 38)
				.kunci(50, LEAN, 40, HX, 22, RAX, -68, LAX, -68)
				.kunci(70, LEAN, 0, HX, 0, RAX, 0, RAY, 0, LAX, 0, LAY, 0)
				.wajahPada(50, Wajah.MATA_SEDIH, -1)
				.bangun());
		// 265: compensation: both hands offer the pouch with a bow, then let it go
		GerakKatalog.daftar(b(SeteruIds.KLIP_GANTI_RUGI, "ganti_rugi", 74).sekali().wajah(Wajah.MATA_TUTUP_TENANG, Wajah.MULUT_DIAM)
				.prop(SeteruIds.PROP_KANTONG_GANTI_RUGI, 0, 0)
				.kunci(0, RAX, -20, LAX, -10, LEAN, 0, HX, 0)
				.kunciE(12, Easing.SINE_IO, RAX, -72, RAY, -24, LAX, -72, LAY, 24, LEAN, 16, HX, 10)
				.kunci(48, RAX, -74, RAY, -24, LAX, -74, LAY, 24, LEAN, 18, HX, 12)
				.kunci(74, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.propPada(52, PROP_SLOT_KANAN, 0).suara(52, "item.bundle.drop_contents", 0.4F, 1.2F)
				.bangun());
		// 266: peace: the salute, a deeper bow and a small smile
		GerakKatalog.daftar(b(SeteruIds.KLIP_DAMAI, "damai", 56).sekali().wajah(Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				.kunci(0, RAX, 0, LAX, 0, LEAN, 0, HX, 0)
				.kunciE(8, Easing.CUBIC_OUT, RAX, -80, RAY, -36, LAX, -78, LAY, 40, LEAN, 12, HX, 8)
				.kunci(20, LEAN, 26, HX, 16)
				.kunci(36, LEAN, 8, HX, 2)
				.kunci(56, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.wajahPada(30, Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.bangun());
	}

	// ================================================================== moods of the feud (D-39F): glare, bully, caught, rivals, grudge, laugh

	private static void suasana() {
		// 268: T0, the glare: arms folded high, the weight on one hip, the head turned half away; a sharp toss of the chin
		// with a snort, then the eyes slide back to the other
		GerakKatalog.daftar(b(SeteruIds.KLIP_MELIRIK, "melirik", 50).sekali().bendera(ARAH_SASARAN).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_CEMBERUT)
				.kunci(0, RAX, -57, RAY, -44, RAZ, 3, LAX, -54, LAY, 44, LAZ, -3, HY, 0, HX, 0, BZ, 0.0F)
				.kunciE(8, Easing.CUBIC_OUT, HY, -28, HX, -6, BZ, 1.2F, TWIST, -6)
				.kunci(18, HY, -30, HX, -7)
				.kunciE(22, Easing.BACK_OUT, HX, -16, HZ, 5)
				.kunci(28, HX, -12, HZ, 3)
				.kunciE(40, Easing.SINE_IO, HY, -8, HX, -8, HZ, 0, TWIST, -2)
				.kunci(50, RAX, -57, RAY, -44, RAZ, 3, LAX, -54, LAY, 44, LAZ, -3, HY, 0, HX, -6, BZ, 1.2F, TWIST, 0)
				.wajahPada(10, Wajah.MATA_LIRIK_KANAN, -1).wajahPada(22, Wajah.MATA_TUTUP, Wajah.MULUT_MENIUP)
				.wajahPada(28, Wajah.MATA_SETENGAH, Wajah.MULUT_CEMBERUT).wajahPada(40, Wajah.MATA_LIRIK_KIRI, -1)
				.partikel(22, "uap", 2, 0)
				.varian(SY_P, v -> v.warisi().kunci(0, BZ, 2.0F, HZ, -4).kunci(50, BZ, 2.0F, HZ, -3))
				.bangun());
		// 269: the extortion: leans in over the victim, the left palm held out and flicked for coins, the right index poking
		// the victim's chest twice; a mean grin
		GerakKatalog.daftar(b(SeteruIds.KLIP_PALAK, "palak", 48).loop().bendera(BICARA | ARAH_SASARAN)
				.wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_MENYERINGAI)
				.kunci(0, LEAN, 14, HX, 2, LAX, -66, LAY, 12, LAZ, -14, RAX, -40, RAZ, 6, BAHU, 0.4F)
				.kunciE(6, Easing.CUBIC_OUT, LAX, -72, LAZ, -22)
				.kunci(10, LAX, -66, LAZ, -14)
				.kunciE(18, Easing.CUBIC_OUT, RAX, -82, RAY, -10, LEAN, 18)
				.kunci(22, RAX, -60, LEAN, 15)
				.kunciE(28, Easing.CUBIC_OUT, RAX, -84, RAY, -10, LEAN, 19)
				.kunci(34, RAX, -42, LEAN, 14, HZ, 6)
				.kunci(48, LEAN, 14, HX, 2, LAX, -66, LAY, 12, LAZ, -14, RAX, -40, RAZ, 6, HZ, 0, BAHU, 0.4F)
				.wajahPada(18, Wajah.MATA_MARAH, Wajah.MULUT_A).wajahPada(34, Wajah.MATA_SETENGAH, Wajah.MULUT_MENYERINGAI)
				.osc(BZ, 0.5F, 24.0F)
				.bangun());
		// 270: found out: a start (both arms jerk up, a hop back), a hand over the mouth, then both hands hidden behind the
		// back and an innocent look aside
		GerakKatalog.daftar(b(SeteruIds.KLIP_KETAHUAN, "ketahuan", 56).sekali().wajah(Wajah.MATA_KAGET, Wajah.MULUT_KAGET)
				.kunci(0, RAX, 0, LAX, 0, LIFT, 0, LEAN, 0, HX, 0)
				.kunciE(4, Easing.CUBIC_OUT, RAX, -120, RAZ, 20, LAX, -116, LAZ, -20, LIFT, 0.22F, LEAN, -10, HX, -12)
				.kunciE(10, Easing.CUBIC_IN, LIFT, 0, LEAN, -6)
				.kunci(16, RAX, -128, RAY, -30, RAZ, 4, LAX, -20, LAZ, -6, HX, 6)
				.kunci(30, RAX, -124, RAY, -28, HX, 8)
				.kunciE(38, Easing.SINE_IO, RAX, 30, RAY, 0, RAZ, -14, LAX, 30, LAZ, 14, LEAN, 2, HX, -4, HY, 24)
				.kunci(56, RAX, 28, RAZ, -14, LAX, 28, LAZ, 14, LEAN, 2, HX, -4, HY, 26)
				.partikel(4, "percik", 3, 0xFFFFFF)
				.wajahPada(16, Wajah.MATA_MALU, Wajah.MULUT_MENGGIGIT_BIBIR).wajahPada(38, Wajah.MATA_LIRIK_ATAS, Wajah.MULUT_SENYUM)
				.bangun());
		// 271: lifelong rivals: the sword arm thrust out, blade pointed at the other ("next season"), held; then the fist
		// returns to the heart, a short nod, chin up
		GerakKatalog.daftar(b(SeteruIds.KLIP_SAINGAN, "saingan", 66).sekali().bendera(ARAH_SASARAN).wajah(Wajah.MATA_KILAT, Wajah.MULUT_MENYERINGAI)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LEAN, 0, HX, 0)
				.kunciE(8, Easing.BACK_OUT, RAX, -92, RAY, -4, RAZ, 2, LAX, 24, LAZ, 10, LEAN, -4, HX, -6, TWIST, 10)
				.kunci(28, RAX, -90, RAY, -4, TWIST, 10)
				.kunciE(36, Easing.CUBIC_OUT, RAX, -64, RAY, -48, RAZ, 6, TWIST, 0, LEAN, 0)
				.kunciE(44, Easing.SINE_IO, HX, 12, LEAN, 4)
				.kunci(50, HX, -8, LEAN, -2)
				.kunci(66, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAZ, 0, LEAN, 0, HX, 0)
				.partikel(8, "kilau", 2, 0xBFE3FF).suara(8, "item.armor.equip_iron", 0.4F, 1.4F)
				.wajahPada(36, Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.varian(SY_P, v -> v.warisi().kunci(28, RAX, -88, RAY, -8, TWIST, 6))
				.bangun());
		// 272: a grudge: the body turns away, the right fist trembles at the side, the head looks back over the shoulder
		// with burning eyes, then it walks on (the legs keep walking)
		GerakKatalog.daftar(b(SeteruIds.KLIP_DENDAM, "dendam", 60).sekali().lapisan(Klip.Lapisan.ATAS).bendera(JALAN_OK)
				.mods(MOD_GEMETAR, 0.3F).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, RAX, -10, RAZ, 8, LAX, 0, HY, 0, TWIST, 0, LEAN, 0)
				.kunciE(10, Easing.CUBIC_OUT, RAX, -18, RAZ, 14, TWIST, -14, LEAN, 6, HX, 8)
				.kunci(22, RAX, -20, RAZ, 14, HX, 10)
				.kunciE(30, Easing.CUBIC_OUT, HY, 52, HX, -4, TWIST, 6)
				.kunci(46, HY, 50, HX, -4)
				.kunciE(54, Easing.SINE_IO, HY, 0, TWIST, 0, HX, 4)
				.kunci(60, RAX, -10, RAZ, 8, HY, 0, TWIST, 0, LEAN, 0, HX, 0)
				.wajahPada(10, Wajah.MATA_TUTUP, -1).wajahPada(30, Wajah.MATA_KILAT, Wajah.MULUT_MARAH)
				.bangun());
		// 273: the mocking laugh: one hand on the belly, the other slapping the thigh, the head thrown back, shoulders bobbing
		GerakKatalog.daftar(b(SeteruIds.KLIP_TERTAWA, "tertawa", 36).loop().wajah(Wajah.MATA_TUTUP, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, -38, RAY, -30, RAZ, 4, LAX, -8, LAZ, -10, HX, -20, LEAN, -8, BAHU, 0.0F)
				.kunciE(5, Easing.CUBIC_OUT, BAHU, 0.6F, HX, -24)
				.kunci(9, BAHU, 0.0F, LAX, -24, LEAN, 4, HX, -10)
				.kunciE(14, Easing.CUBIC_OUT, LAX, 6, LAZ, -14, BAHU, 0.6F)
				.kunci(18, BAHU, 0.0F, HX, -22, LEAN, -8)
				.kunciE(23, Easing.CUBIC_OUT, BAHU, 0.6F)
				.kunci(27, BAHU, 0.0F)
				.kunciE(32, Easing.CUBIC_OUT, BAHU, 0.5F, HX, -18)
				.kunci(36, RAX, -38, RAY, -30, RAZ, 4, LAX, -8, LAZ, -10, HX, -20, LEAN, -8, BAHU, 0.0F)
				.wajahPada(18, Wajah.MATA_SENANG, -1).wajahPada(32, Wajah.MATA_TUTUP, -1)
				.osc(BOB, 0.4F, 9.0F)
				.varian(Klip.sifat(Sifat.SOMBONG.ordinal()), v -> v.warisi().kunci(0, HX, -26).kunci(36, HX, -26))
				.bangun());
	}

	private SeteruPoses() {
	}
}

package dev.kultivator.client.sosok.gerak;

import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;

import java.util.function.Consumer;

import static dev.kultivator.client.sosok.gerak.Klip.*;

/**
 * The canonical clips 1-107 (DESIGN 40.5.10): the keys, lengths, props and events of DESIGN 38.N.5.1-38.N.5.3 (1-78)
 * and 38.T.8.1 (79-107), typed in degrees, with the changes of 40.5.10 that fold the existing poses into data (the four
 * duty poses of {@code DutyPoses} and the work and greeting poses of {@code NpcPoses}, numbers converted from their
 * radians). Clip 19 does not exist. Names come from {@link GerakId}. Once clips start and end at rest so the layer's
 * own fade carries them; loops close on themselves.
 */
final class KatalogTempat {

	static void daftarkan(Consumer<Klip> d) {
		gerakDanSikap(d);
		kerjaDanSapaan(d);
		latihanDanFestival(d);
		tema(d);
	}

	private static Klip.Pembangun b(int id, int durasi) {
		return Klip.baru(id, GerakId.namaAtauId(id), durasi);
	}

	// ================================================================== 38.N.5.1 movement and stances

	private static void gerakDanSikap(Consumer<Klip> d) {
		d.accept(b(GerakId.JALAN_SANTAI, 40).loop().lapisan(Lapisan.ATAS).bendera(JALAN_OK)
				.kunci(0, RAX, 15, LAX, 15, RAZ, 8, LAZ, -8, RAY, -30, LAY, 30, HX, 5).bangun());
		d.accept(b(GerakId.JALAN_TERGESA, 20).loop().bendera(JALAN_OK).kunci(0, LEAN, 8).bangun());
		d.accept(b(GerakId.JALAN_TUA, 50).loop().bendera(JALAN_OK)
				.kunci(0, LEAN, 18, HX, -10, RAX, -35, RAZ, 10).prop(PropId.TONGKAT, 0, 0).bangun());
		d.accept(b(GerakId.JALAN_BAWA, 30).loop().bendera(JALAN_OK | DUA_TANGAN)
				.kunci(0, RAX, -62, LAX, -62, RAY, -14, LAY, 14, LEAN, 4)
				.pasang(PropId.PETI, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F)
				.varian(GerakId.V_JALAN_BAWA_PUNGGUNG, v -> v.tanpaProp().tanpaBendera(DUA_TANGAN)
						.kunci(0, RAX, -20, RAY, 15, LAX, -20, LAY, -15, LEAN, 6).prop(0, 0, PropId.RANGKA_PUNGGUNG))
				.bangun());
		d.accept(b(GerakId.JALAN_PIKUL, 30).loop().bendera(JALAN_OK)
				.kunci(0, RAX, -160, RAZ, 25, LAX, 10)
				.osc(LIFT, 0.03F, 15.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).prop(0, 0, PropId.PIKULAN)
				// the disciple's water duty (DutyPoses WATER, radians -> degrees): pole on the right shoulder
				.varian(GerakId.V_JALAN_PIKUL_DIAM, v -> v.tanpaProp().bendera(PROP_LAMA)
						.kunci(0, RAX, -166, RAZ, -11.5F, LAX, -14.3F, LAZ, -8.6F, LEAN, 3, HZ, -3.4F)
						.osc(RAX, 2.3F, 25.1F).osc(LAX, -2.3F, 25.1F))
				.bangun());
		d.accept(b(GerakId.JALAN_ANAK, 8).loop().bendera(JALAN_OK)
				.kunci(0, LIFT, 0).kunciE(4, Easing.CUBIC_OUT, LIFT, 0.06F).bangun());
		d.accept(b(GerakId.BARIS_JAGA, 30).loop().lapisan(Lapisan.ATAS).bendera(JALAN_OK)
				.kunci(0, RAX, -30, RAZ, 3, LAX, 0, LAZ, -2, HX, -3).prop(PropId.TOMBAK, 0, 0).bangun());
		d.accept(b(GerakId.LARI, 12).loop().bendera(JALAN_OK).kunci(0, LEAN, 15).bangun());
		d.accept(b(GerakId.LARI_TAKUT, 24).loop().bendera(JALAN_OK).wajah(Wajah.MATA_KAGET, Wajah.MULUT_KAGET)
				.kunci(0, LEAN, 20, RAX, -150, LAX, -150, RAZ, 30, LAZ, -30, HY, 0)
				.kunciE(5, Easing.CUBIC_OUT, HY, 55).kunci(9, HY, 55).kunci(13, HY, 0).bangun());
		d.accept(b(GerakId.DUDUK_KURSI, 40).loop().bendera(DUDUK | KAKI_PAKSA)
				.kunci(0, RLX, -90, LLX, -90, RLY, 4, LLY, -4, LIFT, -0.5F, RAX, -20, LAX, -20, RAZ, 3, LAZ, -3).bangun());
		d.accept(b(GerakId.DUDUK_LANTAI, 40).loop().bendera(DUDUK | KAKI_PAKSA)
				.kunci(0, RLX, -80, LLX, -80, RLY, 35, LLY, -35, RLZ, 20, LLZ, -20, LIFT, -0.65F, RAX, -25, LAX, -25).bangun());
		d.accept(b(GerakId.NAPAS_MEDITASI, 80).loop().bendera(DUDUK | BERSILA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, LIFT, 0).kunci(40, LIFT, 0.015F).bangun());
		d.accept(b(GerakId.TIDUR, 120).loop().bendera(BERBARING | MATA_TUTUP | KAKI_PAKSA).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_TIDUR)
				.kunci(0, RAX, -8, LAX, -8, RAZ, 6, LAZ, -6, RLX, 0, LLX, 0, HX, -6).osc(LIFT, 0.01F, 120.0F)
				// on the side (ACT 51 in a room): knees drawn up, hands under the cheek
				.varian(GerakId.V_TIDUR_MIRING, v -> v.kunci(0, RAX, -95, RAY, -35, LAX, -70, LAY, 20, RLX, -45, LLX, -35, HX, 10, HZ, 25)
						.osc(LIFT, 0.01F, 120.0F))
				.bangun());
		d.accept(b(GerakId.TIDUR_DUDUK, 100).loop().bendera(DUDUK | KAKI_PAKSA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_TIDUR)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, RAX, -20, LAX, -20, HX, 35, LEAN, 10)
				.kunci(50, HX, 38).bangun());
		d.accept(b(GerakId.BERDIRI_JAGA, 60).loop().lapisan(Lapisan.ATAS)
				.kunci(0, RAX, -24, RAZ, 3.4F, LAX, -6.9F, LAZ, 6.9F).osc(HY, 25, 60).prop(PropId.TOMBAK, 0, 0).bangun());
		d.accept(b(GerakId.BERLUTUT, 12).tahan().bendera(KAKI_PAKSA)
				.kunci(0, RLX, 0, LLX, 0, LIFT, 0, LEAN, 0, HX, 0)
				.kunciE(12, Easing.CUBIC_OUT, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 15, HX, 20).bangun());
		d.accept(b(GerakId.TAKUT_SEMBUNYI, 20).loop().bendera(KAKI_PAKSA).mods(MOD_GEMETAR, 2.0F)
				.wajah(Wajah.MATA_KAGET, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, LEAN, 50, RAX, -170, LAX, -170, RAZ, 20, LAZ, -20, LIFT, -0.3F, RLX, -50, LLX, -40).bangun());
		d.accept(b(GerakId.KAGET, 10).sekali().wajah(Wajah.MATA_KAGET, Wajah.MULUT_KAGET)
				.kunci(0, LIFT, 0, RAX, 0, LAX, 0, HX, 0)
				.kunciE(3, Easing.CUBIC_OUT, LIFT, 0.15F, RAX, -60, LAX, -60, HX, -15, RAZ, 12, LAZ, -12)
				.kunci(10, LIFT, 0, RAX, 0, LAX, 0, HX, 0, RAZ, 0, LAZ, 0)
				// 40.5.6 variants: children jump with both arms flung up, elders stagger back with a hand on the heart,
				// civilian women clap both hands to the mouth with the shoulders drawn up
				.varian(SY_ANAK, v -> v.durasi(14)
						.kunci(0, LIFT, 0, RAX, 0, LAX, 0, RAZ, 0, LAZ, 0, HX, 0)
						.kunciE(3, Easing.CUBIC_OUT, LIFT, 0.25F, RAX, -150, LAX, -150, RAZ, 25, LAZ, -25, HX, -20)
						.kunciE(8, Easing.CUBIC_IN, LIFT, 0).kunci(10, RAX, -140, LAX, -140)
						.kunci(14, LIFT, 0, RAX, 0, LAX, 0, RAZ, 0, LAZ, 0, HX, 0))
				.varian(SY_TUA, v -> v.durasi(18).mods(MOD_GEMETAR, 0.8F)
						.kunci(0, LEAN, 0, LIFT, 0, RAX, 0, RAY, 0, LAX, 0, HX, 0)
						.kunciE(5, Easing.CUBIC_OUT, LEAN, -10, LIFT, 0.05F, RAX, -45, RAY, -30, LAX, -30, HX, -8)
						.kunci(12, LEAN, -6, LIFT, 0, RAX, -45, RAY, -30, LAX, -25, HX, -6)
						.kunci(18, LEAN, 0, LIFT, 0, RAX, 0, RAY, 0, LAX, 0, HX, 0))
				.varian(SY_P | SY_SIPIL, v -> v.durasi(14)
						.kunci(0, LIFT, 0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, HX, 0, BAHU, 0)
						.kunciE(3, Easing.CUBIC_OUT, LIFT, 0.08F, RAX, -122, RAY, -48, LAX, -116, LAY, 44, HX, -10, BAHU, 0.5F)
						.kunci(10, LIFT, 0, RAX, -122, RAY, -48, LAX, -116, LAY, 44, HX, -6, BAHU, 0.4F)
						.kunci(14, LIFT, 0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, HX, 0, BAHU, 0))
				.bangun());
	}

	// ================================================================== 38.N.5.2 work, eating, greetings

	private static void kerjaDanSapaan(Consumer<Klip> d) {
		d.accept(b(GerakId.MAKAN, 40).loop().bendera(KERJA).wajah(-1, Wajah.MULUT_MAKAN)
				.kunci(0, LAX, -45, LAY, 22, RAX, -70, HX, 5).kunci(10, RAX, -88, HX, 10).kunci(15, HX, 0).kunci(20, RAX, -70, HX, 10)
				.kunci(25, HX, 0).kunci(30, RAX, -88, HX, 10).kunci(35, HX, 0)
				.prop(PropId.SUMPIT, PropId.MANGKUK, 0).bangun());
		d.accept(b(GerakId.MINUM_TEH, 100).loop()
				.kunci(0, RAX, -40, RAY, 0, HX, 0, LAX, -30, LAY, 10).kunci(20, RAX, -85, RAY, -32, HX, -12).kunci(40, RAX, -85, RAY, -32, HX, -12)
				.kunci(50, RAX, -40, RAY, 0, HX, 0).prop(PropId.CANGKIR, PropId.LEPEK, 0)
				// civilian women shield the sip behind the raised left sleeve (the saucer stays on the table)
				.varian(SY_P | SY_SIPIL, v -> v.prop(PropId.CANGKIR, 0, 0)
						.kunci(0, RAX, -40, RAY, 0, HX, 0, LAX, -30, LAY, 10).kunci(20, RAX, -85, RAY, -32, HX, -12, LAX, -82, LAY, 38)
						.kunci(40, RAX, -85, RAY, -32, HX, -12, LAX, -82, LAY, 38).kunci(50, RAX, -40, RAY, 0, HX, 0, LAX, -30, LAY, 10))
				.bangun());
		d.accept(b(GerakId.TUANG_TEH, 40).sekali().bendera(KERJA)
				.kunci(0, RAX, -55, RAZ, 0, LAX, -40, LAY, 20).kunci(10, RAX, -60, RAZ, -20).kunci(30, RAX, -55, RAZ, 0)
				.partikel(10, "minecraft:falling_water", 1, 0).partikel(16, "minecraft:falling_water", 1, 0)
				.partikel(22, "minecraft:falling_water", 1, 0).prop(PropId.TEKO, PropId.CANGKIR, 0)
				// the tea master's endless pour (NpcPoses tea): p = 0.5 + 0.5 sin over 52 ticks
				.varian(GerakId.V_TUANG_TEH_LOOP, v -> v.loop().durasi(52)
						.kunci(0, RAX, -60, RAY, -14.3F, RAZ, 0, LAX, -48.7F, LAY, 25.8F, HX, 17.2F)
						.osc(RAY, 8.6F, 52.4F, 0.0F, Osilator.Bentuk.SIN_SETENGAH).osc(RAZ, -14.3F, 52.4F, 0.0F, Osilator.Bentuk.SIN_SETENGAH)
						.partikel(20, "minecraft:falling_water", 1, 0))
				.bangun());
		// 40.5.10: the kitchen duty numbers (DutyPoses KITCHEN), one ladle circle per 21 ticks
		d.accept(b(GerakId.MASAK, 21).loop().bendera(KERJA)
				.kunci(0, RAX, -60, RAZ, 0, LAX, -40, LAZ, -8.6F, HX, 26).osc(RAX, 10.3F, 20.9F).osc(RAZ, 11.5F, 20.9F, 0.0F, Osilator.Bentuk.COS)
				.partikel(0, "asap", 1, 0).prop(PropId.SENDOK_SAYUR, 0, 0).bangun());
		d.accept(b(GerakId.SAPU, 24).loop().bendera(KERJA | DUA_TANGAN)
				.kunci(0, LEAN, 20, RAX, -45, LAX, -35, TWIST, -12).kunci(12, TWIST, 12)
				.partikel(6, "debu", 1, 0).partikel(18, "debu", 1, 0)
				.pasang(PropId.SAPU, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		// 40.5.10: the smith's hammer of NpcPoses (slow rise, quick fall), the anvil rings at the bottom
		d.accept(b(GerakId.PALU_LANDASAN, 21).loop().bendera(KERJA)
				.kunci(0, RAX, -28.6F, RAZ, 4.6F, LAX, -40, LAY, 17.2F, HX, 20, LEAN, 6.9F)
				.osc(RAX, -97.4F, 20.9F, 0.0F, Osilator.Bentuk.SIN_KUBIK)
				.suara(16, "block.anvil.use", 0.4F, 1.1F).partikel(16, "percik", 3, 0).prop(PropId.PALU, 0, 0).bangun());
		d.accept(b(GerakId.TUMBUK_HERBA, 14).loop().bendera(KERJA)
				.kunci(0, LEAN, 25, RAX, -50, LAX, -40).kunciE(7, Easing.CUBIC_IN, RAX, -28).suara(7, "block.stone.hit", 0.15F, 1.3F)
				.prop(PropId.ALU, 0, 0).bangun());
		d.accept(b(GerakId.MENULIS, 30).loop().bendera(KERJA | LIRIK_BAWAH)
				.kunci(0, LEAN, 15, HX, 25, RAX, -55, RAY, -10, LAX, -45).kunci(15, RAY, 5).prop(PropId.KUAS, 0, 0).bangun());
		d.accept(b(GerakId.MEMBACA, 80).loop().bendera(LIRIK_BAWAH | DUA_TANGAN)
				.kunci(0, RAX, -60, LAX, -60, RAY, -25, LAY, 25, HX, 22).kunci(70, LAY, 25).kunciE(76, Easing.CUBIC_OUT, LAY, 40)
				.suara(76, "item.book.page_turn", 0.2F, 1.0F)
				.pasang(PropId.KITAB, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		// 40.5.10: the abacus of NpcPoses (fingers flicking every 7 ticks)
		d.accept(b(GerakId.HITUNG_SEMPOA, 14).loop().bendera(KERJA | LIRIK_BAWAH | DUA_TANGAN)
				.kunci(0, RAX, -48.7F, RAY, -20, LAX, -48.7F, LAY, 20, HX, 20).osc(RAX, 6.9F, 7.0F)
				.pasang(PropId.SEMPOA, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.TAWAR, 30).loop().bendera(BICARA)
				.kunci(0, RAX, -70, LAX, -50, LAY, 20, HY, 0).kunci(10, RAX, -45, HY, 0).kunci(12, HY, 12).kunci(15, HY, -12)
				.kunci(18, HY, 12).kunci(20, HY, 0).bangun());
		d.accept(b(GerakId.SERU_DAGANG, 30).sekali().bendera(BICARA).wajah(-1, Wajah.MULUT_A)
				.kunci(0, RAX, 0, RAY, 0, HX, 0, LEAN, 0).kunciE(6, Easing.CUBIC_OUT, RAX, -110, RAY, -50, HX, -10, LEAN, -4)
				.kunci(24, RAX, -110, RAY, -50, HX, -10, LEAN, -4).kunci(30, RAX, 0, RAY, 0, HX, 0, LEAN, 0).bangun());
		hormat(d);
		d.accept(b(GerakId.LAMBAI, 24).sekali().wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, 0, RAZ, 0).kunciE(3, Easing.CUBIC_OUT, RAX, -160, RAZ, 45).kunci(7, RAZ, -5).kunci(11, RAZ, 45)
				.kunci(15, RAZ, -5).kunci(19, RAX, -160, RAZ, 30).kunci(24, RAX, 0, RAZ, 0)
				// children wave both arms and hop; elders lift a slow hand to the shoulder; civilian women wave beside the
				// face with the head tilted and the other hand at the waist
				.varian(SY_ANAK, v -> v.durasi(28).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
						.kunci(0, RAX, 0, RAZ, 0, LAX, 0, LAZ, 0, LIFT, 0)
						.kunciE(3, Easing.CUBIC_OUT, RAX, -165, RAZ, 40, LAX, -165, LAZ, -40)
						.kunci(5, LIFT, 0.06F).kunci(7, RAZ, -5, LAZ, 5).kunci(9, LIFT, 0).kunci(11, RAZ, 40, LAZ, -40).kunci(13, LIFT, 0.06F)
						.kunci(15, RAZ, -5, LAZ, 5).kunci(17, LIFT, 0).kunci(19, RAZ, 40, LAZ, -40).kunci(23, RAX, -165, LAX, -165)
						.kunci(28, RAX, 0, RAZ, 0, LAX, 0, LAZ, 0, LIFT, 0))
				.varian(SY_TUA, v -> v.durasi(34)
						.kunci(0, RAX, 0, RAZ, 0, HX, 0).kunci(6, RAX, -95, RAZ, 20, HX, -4).kunci(12, RAZ, 0).kunci(18, RAZ, 20)
						.kunci(24, RAZ, 0).kunci(28, RAX, -95, RAZ, 10, HX, -4).kunci(34, RAX, 0, RAZ, 0, HX, 0))
				.varian(SY_P | SY_SIPIL, v -> v.durasi(26)
						.kunci(0, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, HZ, 0)
						.kunciE(4, Easing.CUBIC_OUT, RAX, -120, RAY, -20, RAZ, 8, LAX, -25, LAY, 25, HZ, -6)
						.kunci(8, RAZ, -6).kunci(12, RAZ, 8).kunci(16, RAZ, -6)
						.kunci(20, RAX, -120, RAY, -20, RAZ, 8, LAX, -25, LAY, 25, HZ, -6)
						.kunci(26, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, HZ, 0))
				.bangun());
		d.accept(b(GerakId.TUNJUK, 30).sekali().bendera(ARAH_SASARAN)
				.kunci(0, RAX, 0, HX, 0).kunciE(5, Easing.CUBIC_OUT, RAX, -90, HX, -4).kunci(25, RAX, -90, HX, -4).kunci(30, RAX, 0, HX, 0).bangun());
		d.accept(b(GerakId.TERTAWA, 40).sekali().mods(MOD_DETAK, 0).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, LEAN, 0, HX, 0, LAX, 0, LAY, 0).kunciE(6, Easing.CUBIC_OUT, LEAN, -8, HX, -15, LAX, -30, LAY, 40)
				.kunci(34, LEAN, -8, HX, -15, LAX, -30, LAY, 40).kunci(40, LEAN, 0, HX, 0, LAX, 0, LAY, 0)
				.suara(2, "entity.villager.celebrate", 0.3F, 1.2F)
				// children double over holding the belly and bounce; elders lean back stroking the beard; civilian women
				// hide the laugh behind the sleeve, head tilted
				.varian(SY_ANAK, v -> v.durasi(40)
						.kunci(0, LEAN, 0, HX, 0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LIFT, 0)
						.kunciE(6, Easing.CUBIC_OUT, LEAN, 25, HX, 10, RAX, -40, RAY, -30, LAX, -40, LAY, 30)
						.kunci(10, LIFT, 0.04F).kunci(14, LIFT, 0).kunci(18, LIFT, 0.04F).kunci(22, LIFT, 0).kunci(26, LIFT, 0.04F)
						.kunci(30, LIFT, 0).kunci(34, LEAN, 25, HX, 10, RAX, -40, RAY, -30, LAX, -40, LAY, 30)
						.kunci(40, LEAN, 0, HX, 0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LIFT, 0)
						.suara(2, "entity.villager.celebrate", 0.3F, 1.5F))
				.varian(SY_TUA, v -> v.durasi(50)
						.kunci(0, LEAN, 0, HX, 0, RAX, 0, RAY, 0, LAX, 0, LAY, 0)
						.kunciE(8, Easing.CUBIC_OUT, LEAN, -6, HX, -12, RAX, -20, RAY, -20, LAX, -105, LAY, 30)
						.kunci(42, LEAN, -6, HX, -12, RAX, -20, RAY, -20, LAX, -105, LAY, 30)
						.kunci(50, LEAN, 0, HX, 0, RAX, 0, RAY, 0, LAX, 0, LAY, 0).osc(LAX, 4.0F, 12.0F)
						.suara(4, "entity.villager.celebrate", 0.3F, 0.85F))
				.varian(SY_P | SY_SIPIL, v -> v.durasi(36).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
						.kunci(0, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0, HZ, 0)
						.kunciE(5, Easing.CUBIC_OUT, RAX, -122, RAY, -48, RAZ, 6, LAX, -25, LAY, 25, LEAN, 6, HX, 8, HZ, -5)
						.kunci(31, RAX, -122, RAY, -48, RAZ, 6, LAX, -25, LAY, 25, LEAN, 6, HX, 8, HZ, -5)
						.kunci(36, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0, HZ, 0)
						.suara(2, "entity.villager.celebrate", 0.22F, 1.45F))
				.bangun());
		d.accept(b(GerakId.BERDEBAT, 20).loop().bendera(BICARA).wajah(Wajah.MATA_MARAH, -1)
				.kunci(0, LEAN, 10, RAX, -70, LAX, -30, HX, -5).kunci(10, RAX, -30, LAX, -70).bangun());
		// 40.5.10: the speaking hand of NpcPoses (FLAG_TALK); variant 1 the orator's sweep
		d.accept(b(GerakId.BICARA_TANGAN, 30).loop().lapisan(Lapisan.ATAS).bendera(BICARA | JALAN_OK)
				.kunci(0, RAX, -34.4F, RAY, -14.3F, RAZ, 8, HX, 0, HY, 0)
				.osc(RAX, 14.3F, 19.0F).osc(RAY, 5.7F, 37.0F).osc(RAZ, 4.6F, 30.0F).osc(HX, 4.0F, 12.6F).osc(HY, 5.2F, 48.3F)
				.varian(GerakId.V_BICARA_MEGAH, v -> v.warisi().tanpaKunci()
						.kunci(0, RAX, -83, RAY, -14.3F, RAZ, 8, HX, 0, HY, 0)
						.osc(RAX, 23.0F, 19.0F).osc(RAY, 5.7F, 37.0F).osc(RAZ, 4.6F, 30.0F).osc(HX, 4.0F, 12.6F).osc(HY, 5.2F, 48.3F))
				.bangun());
		d.accept(b(GerakId.BISIK, 40).sekali().bendera(BICARA)
				.kunci(0, LEAN, 0, TWIST, 0, LAX, 0, LAY, 0).kunciE(8, Easing.CUBIC_OUT, LEAN, 15, TWIST, 20, LAX, -110, LAY, 40)
				.kunci(32, LEAN, 15, TWIST, 20, LAX, -110, LAY, 40).kunci(40, LEAN, 0, TWIST, 0, LAX, 0, LAY, 0).bangun());
		d.accept(b(GerakId.MARAH_TUNJUK, 30).sekali().bendera(ARAH_SASARAN).mods(MOD_GEMETAR, 1.5F)
				.wajah(Wajah.MATA_MARAH, Wajah.MULUT_MARAH)
				.kunci(0, RAX, 0, LEAN, 0, HX, 0).kunciE(4, Easing.BACK_OUT, RAX, -90, LEAN, 8, HX, -8)
				.kunci(26, RAX, -90, LEAN, 8, HX, -8).kunci(30, RAX, 0, LEAN, 0, HX, 0).bangun());
		d.accept(b(GerakId.ANGKAT_BAWA, 20).sekali().bendera(DUA_TANGAN)
				.kunci(0, LEAN, 40, RAX, -90, LAX, -90, RAY, -10, LAY, 10).kunci(20, LEAN, 4, RAX, -62, LAX, -62, RAY, -14, LAY, 14)
				.pasang(PropId.PETI, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.MEMANCING, 200).loop()
				.kunci(0, RAX, -100, LAX, -30).kunciE(6, Easing.CUBIC_OUT, RAX, -150).kunciE(14, Easing.BACK_OUT, RAX, -45)
				.kunci(180, RAX, -45).kunci(195, RAX, -100).osc(RAX, 1.5F, 37.0F)
				.suara(14, "entity.fishing_bobber.throw", 0.2F, 1.0F).prop(PropId.JORAN, 0, 0).bangun());
		d.accept(b(GerakId.MENCANGKUL, 24).loop().bendera(KERJA | DUA_TANGAN)
				.kunci(0, RAX, -130, LAX, -130, LEAN, 0).kunciE(10, Easing.BACK_OUT, RAX, -20, LAX, -20, LEAN, 20)
				.partikel(10, "tanah", 3, 0).suara(10, "item.hoe.till", 0.15F, 1.0F)
				.pasang(PropId.CANGKUL, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.MEMANEN, 30).loop().bendera(KERJA)
				.kunci(0, LEAN, 45, RAX, -100, LAX, -40).kunci(15, RAX, -40).prop(0, PropId.KERANJANG, 0)
				// the herb garden duty (DutyPoses GARDEN): bent over with the sickle, the old bend() is now LEAN
				.varian(GerakId.V_MEMANEN_SABIT, v -> v.tanpaProp().bendera(PROP_LAMA)
						.kunci(0, LEAN, 31.5F, HX, 34.4F, RAX, -63, RAZ, 5.7F, LAX, -43, LAY, 8.6F, LAZ, -5.7F).osc(RAX, 17.2F, 34.9F))
				.bangun());
		d.accept(b(GerakId.TANAM_BIBIT, 40).loop().bendera(KAKI_PAKSA | KERJA)
				.kunci(0, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 30, HX, 25, RAX, -60, LAX, -25)
				.kunci(20, RAX, -20, LEAN, 36).prop(PropId.BIBIT, 0, 0).bangun());
		d.accept(b(GerakId.ANGKAT_AIR, 30).loop().bendera(JALAN_OK)
				.kunci(0, RAX, -20, LAX, -20, RAZ, 15, LAZ, -15, LEAN, 4).osc(RAZ, 2.0F, 15.0F).osc(LAZ, -2.0F, 15.0F)
				.prop(PropId.EMBER, PropId.EMBER, 0).bangun());
		d.accept(b(GerakId.SUJUD_DUPA, 90).sekali().bendera(MATA_TUTUP | DUA_TANGAN).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, RAX, -90, LAX, -90, RAY, -40, LAY, 40, LEAN, 0).kunci(15, LEAN, 30).kunci(30, LEAN, 0).kunci(45, LEAN, 30)
				.kunci(60, LEAN, 0).kunci(75, LEAN, 30).kunci(90, LEAN, 0)
				.partikel(15, "asap", 2, 0).partikel(45, "asap", 2, 0).partikel(75, "asap", 2, 0)
				.pasang(PropId.DUPA, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.BASUH_MUKA, 60).sekali().wajah(Wajah.MATA_TUTUP, -1)
				.kunci(0, LEAN, 30, RAX, -80, LAX, -80).kunci(15, RAX, -120, LAX, -120).kunci(30, RAX, -80, LAX, -80)
				.kunci(45, RAX, -120, LAX, -120).kunci(60, RAX, -80, LAX, -80)
				.partikel(15, "minecraft:splash", 6, 0).partikel(45, "minecraft:splash", 6, 0).bangun());
		d.accept(b(GerakId.GENDERANG, 16).loop()
				.kunci(0, RAX, -140, LAX, -60).kunciE(8, Easing.CUBIC_IN, RAX, -60, LAX, -140)
				.suara(0, "block.note_block.basedrum", 0.4F, 0.8F).suara(8, "block.note_block.basedrum", 0.4F, 0.85F)
				.prop(PropId.PEMUKUL, PropId.PEMUKUL, 0).bangun());
		d.accept(b(GerakId.PALU_HAKIM, 30).sekali()
				.kunci(0, RAX, -30).kunci(4, RAX, -120).kunciE(8, Easing.BACK_OUT, RAX, -50).kunci(24, RAX, -50).kunci(30, RAX, 0)
				.suara(8, "block.wood.hit", 0.6F, 0.9F).prop(PropId.PALU_KAYU, 0, 0).bangun());
		d.accept(b(GerakId.LEMPAR_PAKAN, 24).sekali().bendera(KERJA)
				.kunci(0, RAX, -60, RAZ, 0, LAX, -35, LAZ, 15).kunciE(10, Easing.BACK_OUT, RAX, -110, RAZ, 20).kunci(24, RAX, -60, RAZ, 0)
				.partikel(10, "butir", 6, 0).prop(0, PropId.KERANJANG, 0)
				// the farm duty (DutyPoses FARM): a throw every 28.6 ticks from the basket on the left arm
				.varian(GerakId.V_LEMPAR_PAKAN_LOOP, v -> v.loop().durasi(29).tanpaProp().bendera(PROP_LAMA)
						.kunci(0, RAX, -51.6F, RAZ, -14.3F, LAX, -31.5F, LAZ, 17.2F, HX, 17.2F)
						.osc(RAX, -28.6F, 28.56F, 0.0F, Osilator.Bentuk.SIN_POSITIF).osc(RAZ, -28.6F, 28.56F, 0.0F, Osilator.Bentuk.SIN_POSITIF))
				.bangun());
		d.accept(b(GerakId.KIPAS, 20).loop()
				.kunci(0, RAX, -80, RAY, -40, RAZ, 10).kunci(10, RAZ, -10).prop(PropId.KIPAS_LIPAT, 0, 0).bangun());
		d.accept(b(GerakId.CATUR, 80).loop().bendera(DUDUK | KAKI_PAKSA | LIRIK_BAWAH)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, LEAN, 20, HX, 30, RAX, -20, LAX, -20).kunci(66, RAX, -20)
				.kunciE(70, Easing.CUBIC_OUT, RAX, -70).kunci(76, RAX, -20).suara(70, "block.wood.place", 0.12F, 1.6F).bangun());
		d.accept(b(GerakId.CERITA, 40).loop().bendera(DUDUK | KAKI_PAKSA | BICARA)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, RAX, -34, RAZ, 30, LAX, -20, HX, 0)
				.osc(RAX, 18.0F, 19.0F).osc(RAZ, 8.0F, 30.0F).osc(HX, 4.0F, 12.6F).bangun());
		d.accept(b(GerakId.HANGAT_TANGAN, 40).loop()
				.kunci(0, RAX, -70, LAX, -70, RAY, -30, LAY, 30).osc(RAZ, 3.0F, 40.0F).osc(LAZ, -3.0F, 40.0F).bangun());
		// 40.5.10: NpcPoses FLAG_GAZE; variant 1 points up while talking, variant 2 the musing of ACT 53
		d.accept(b(GerakId.TATAP_LANGIT, 80).loop().lapisan(Lapisan.ATAS).bendera(LIRIK_ATAS)
				.kunci(0, HX, -45.8F, RAZ, 4.1F, LAZ, -4.1F)
				.varian(GerakId.V_TATAP_TUNJUK, v -> v.bendera(BICARA).kunci(0, RAX, -143, RAY, -5.7F, RAZ, 5.7F, HX, -40))
				.varian(GerakId.V_TATAP_RENUNG, v -> v.tanpaBendera(LIRIK_ATAS)
						.kunci(0, HX, -20, RAX, 28.6F, RAZ, -12.6F, LAX, 28.6F, LAZ, 12.6F).kait(0, "mandiri:kelopak"))
				.bangun());
		d.accept(b(GerakId.BASUH_PAKAIAN, 30).loop().bendera(KERJA | DUA_TANGAN)
				.kunci(0, LEAN, 35, RAX, -70, LAX, -70).kunci(15, RAX, -50, LAX, -50).partikel(15, "minecraft:splash", 3, 0)
				.pasang(PropId.KAIN, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F)
				// hanging the washing on the line: the wet cloth hangs down from both raised hands
				.varian(GerakId.V_BASUH_JEMUR, v -> v.tanpaBendera(KERJA).tanpaProp()
						.kunci(0, RAX, -150, LAX, -150, RAY, -10, LAY, 10, HX, -20).osc(RAZ, 5.0F, 30.0F).osc(LAZ, -5.0F, 30.0F)
						.pasang(PropId.KAIN, Titik.DUA_TANGAN, 0, 3, 0, 90, 0, 0, 1.0F))
				.bangun());
		d.accept(b(GerakId.RAK_SUSUN, 40).loop().bendera(KERJA)
				.kunci(0, RAX, -120, HX, -15).kunci(20, RAX, -70, HX, 10).prop(PropId.KITAB, 0, 0).bangun());
		d.accept(b(GerakId.HUNUS, 8).sekali()
				.kunci(0, RAX, -60, RAY, 25, RAZ, 0).kunciE(8, Easing.CUBIC_OUT, RAX, -20, RAY, 0, RAZ, 10)
				.suara(4, "item.armor.equip_iron", 0.3F, 1.4F).bangun());
	}

	/** 32/33 (40.5.10, 40.5.6): baoquan from NpcPoses' FLAG_BOW numbers; wanfu for civilian women or when asked. */
	private static void hormat(Consumer<Klip> d) {
		Consumer<Klip.Pembangun> wanfu = v -> v.bendera(KAKI_PAKSA)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, RLX, 0, LLX, 0, LIFT, 0, HX, 0, LEAN, 0, BZ, 0)
				.kunciE(6, Easing.CUBIC_OUT, RAX, -28, RAY, -42, LAX, -32, LAY, 18)
				.kunci(18, RLX, 12, LLX, -8, LIFT, -0.08F, HX, 15, LEAN, 8, BZ, -3)
				.kunci(24, RLX, 12, LLX, -8, LIFT, -0.08F, HX, 15, LEAN, 8, BZ, -3, RAX, -28, RAY, -42, LAX, -32, LAY, 18)
				.kunci(30, RAX, 0, RAY, 0, LAX, 0, LAY, 0, RLX, 0, LLX, 0, LIFT, 0, HX, 0, LEAN, 0, BZ, 0);
		Consumer<Klip.Pembangun> baoquan = v -> v
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.kunciE(6, Easing.CUBIC_OUT, RAX, -74.5F, RAY, -35.5F, LAX, -71.6F, LAY, 35.5F)
				.kunci(18, LEAN, 18.3F, HX, 17.2F)
				.kunci(24, LEAN, 18.3F, HX, 17.2F, RAX, -74.5F, RAY, -35.5F, LAX, -71.6F, LAY, 35.5F)
				.kunci(30, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0);
		// children: an eager quick baoquan, a deep bob and a little pop back up; elders: slow and shallow, hands trembling
		Consumer<Klip.Pembangun> anak = v -> v.durasi(24)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0, LIFT, 0)
				.kunciE(4, Easing.CUBIC_OUT, RAX, -80, RAY, -35, LAX, -76, LAY, 35)
				.kunciE(10, Easing.CUBIC_OUT, LEAN, 35, HX, 25).kunci(16, LEAN, 35, HX, 25)
				.kunciE(20, Easing.BACK_OUT, LEAN, 0, HX, 0, LIFT, 0).kunci(21, LIFT, 0.04F)
				.kunci(24, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0, LIFT, 0);
		Consumer<Klip.Pembangun> tua = v -> v.durasi(40).mods(MOD_GEMETAR, 0.5F)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.kunci(10, RAX, -70, RAY, -32, LAX, -68, LAY, 32).kunci(20, LEAN, 10, HX, 12)
				.kunci(32, LEAN, 10, HX, 12, RAX, -70, RAY, -32, LAX, -68, LAY, 32)
				.kunci(40, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0);
		Klip.Pembangun h = b(GerakId.HORMAT, 30).sekali();
		baoquan.accept(h);
		d.accept(h.varian(GerakId.V_HORMAT_WANFU, wanfu).varian(GerakId.V_HORMAT_BAOQUAN, baoquan)
				.varian(SY_ANAK, anak).varian(SY_TUA, tua).varian(SY_P | SY_SIPIL, wanfu).bangun());
		Consumer<Klip.Pembangun> wanfuDalam = v -> v.durasi(50).bendera(KAKI_PAKSA)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, RLX, 0, LLX, 0, LIFT, 0, HX, 0, LEAN, 0, BZ, 0)
				.kunciE(6, Easing.CUBIC_OUT, RAX, -28, RAY, -42, LAX, -32, LAY, 18)
				.kunci(14, RLX, 28, LLX, -16, LIFT, -0.16F, HX, 22, LEAN, 24, BZ, -4)
				.kunci(40, RLX, 28, LLX, -16, LIFT, -0.16F, HX, 22, LEAN, 24, BZ, -4, RAX, -28, RAY, -42, LAX, -32, LAY, 18)
				.kunci(50, RAX, 0, RAY, 0, LAX, 0, LAY, 0, RLX, 0, LLX, 0, LIFT, 0, HX, 0, LEAN, 0, BZ, 0);
		Consumer<Klip.Pembangun> baoquanDalam = v -> v.durasi(50)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.kunciE(6, Easing.CUBIC_OUT, RAX, -74.5F, RAY, -35.5F, LAX, -71.6F, LAY, 35.5F)
				.kunci(10, LEAN, 55, HX, 25)
				.kunci(40, LEAN, 55, HX, 25, RAX, -74.5F, RAY, -35.5F, LAX, -71.6F, LAY, 35.5F)
				.kunci(50, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0);
		Consumer<Klip.Pembangun> anakDalam = v -> v.durasi(40)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.kunciE(4, Easing.CUBIC_OUT, RAX, -80, RAY, -35, LAX, -76, LAY, 35)
				.kunciE(10, Easing.CUBIC_OUT, LEAN, 62, HX, 28).kunci(30, LEAN, 62, HX, 28)
				.kunci(40, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0);
		Consumer<Klip.Pembangun> tuaDalam = v -> v.durasi(60).mods(MOD_GEMETAR, 0.5F)
				.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.kunci(12, RAX, -70, RAY, -32, LAX, -68, LAY, 32).kunci(26, LEAN, 30, HX, 18)
				.kunci(48, LEAN, 30, HX, 18, RAX, -70, RAY, -32, LAX, -68, LAY, 32)
				.kunci(60, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0);
		Klip.Pembangun hd = b(GerakId.HORMAT_DALAM, 50).sekali();
		baoquanDalam.accept(hd);
		d.accept(hd.varian(GerakId.V_HORMAT_WANFU, wanfuDalam).varian(GerakId.V_HORMAT_BAOQUAN, baoquanDalam)
				.varian(SY_ANAK, anakDalam).varian(SY_TUA, tuaDalam).varian(SY_P | SY_SIPIL, wanfuDalam).bangun());
	}

	// ================================================================== 38.N.5.3 drills, children, festivals, repairs

	private static void latihanDanFestival(Consumer<Klip> d) {
		d.accept(pedang(b(GerakId.LATIHAN_PEDANG_1, 120))
				.duel(DuelPose.KUDA_TENGAH, 20).duel(DuelPose.TEBAS_ATAS, 10).duel(DuelPose.TEBAS_KANAN, 10).duel(DuelPose.TUSUK, 10)
				.duel(DuelPose.PUTAR, 15).duel(DuelPose.KUDA_TINGGI, 20).duel(DuelPose.TEBAS_BAWAH_KIRI, 10).duel(DuelPose.TANGKIS, 10)
				.duel(DuelPose.KUDA_TENGAH, 15)
				.suara(30, "entity.player.attack.sweep", 0.2F, 1.2F).suara(40, "entity.player.attack.sweep", 0.2F, 1.1F)
				.suara(50, "entity.player.attack.sweep", 0.2F, 1.3F).suara(95, "entity.player.attack.sweep", 0.2F, 1.0F).bangun());
		d.accept(pedang(b(GerakId.LATIHAN_PEDANG_2, 96))
				.duel(DuelPose.KUDA_TENGAH, 16).duel(DuelPose.TEBAS_ATAS_KANAN, 8).duel(DuelPose.TEBAS_ATAS_KIRI, 8).duel(DuelPose.TUSUK, 8)
				.duel(DuelPose.TEBAS_ATAS_KANAN, 8).duel(DuelPose.TEBAS_ATAS_KIRI, 8).duel(DuelPose.PUTAR, 12).duel(DuelPose.KUDA_TINGGI, 16)
				.duel(DuelPose.TANGKIS, 8).duel(DuelPose.KUDA_TENGAH, 4)
				.suara(24, "entity.player.attack.sweep", 0.2F, 1.3F).suara(32, "entity.player.attack.sweep", 0.2F, 1.25F)
				.suara(48, "entity.player.attack.sweep", 0.2F, 1.3F).suara(56, "entity.player.attack.sweep", 0.2F, 1.25F).bangun());
		d.accept(pedang(b(GerakId.LATIHAN_PEDANG_3, 90)).bendera(DUA_TANGAN)
				.duel(DuelPose.ANGKAT_BERAT, 30).duel(DuelPose.TEBAS_ATAS, 15).duel(DuelPose.HENTAK, 15).duel(DuelPose.SIKAP_PEMBUKA, 30)
				.suara(45, "entity.player.attack.strong", 0.25F, 0.8F).suara(52, "entity.player.big_fall", 0.15F, 1.0F).bangun());
		d.accept(b(GerakId.LATIHAN_TINJU, 20).loop().bendera(KAKI_PAKSA)
				.kunci(0, RLZ, 25, LLZ, -25, RLX, -25, LLX, -25, LIFT, -0.25F, RAX, -20, LAX, -20, RAY, 0, LAY, 0, TWIST, 0)
				.kunciE(3, Easing.BACK_OUT, RAX, -90, RAY, -10, TWIST, 12).kunci(8, RAX, -20, RAY, 0, TWIST, 0)
				.kunciE(13, Easing.BACK_OUT, LAX, -90, LAY, 10, TWIST, -12).kunci(18, LAX, -20, LAY, 0, TWIST, 0)
				.suara(3, "entity.player.attack.weak", 0.15F, 1.3F).suara(13, "entity.player.attack.weak", 0.15F, 1.25F).bangun());
		d.accept(b(GerakId.LATIHAN_TOMBAK, 30).loop().bendera(DUA_TANGAN)
				.kunci(0, LEAN, 10, RAX, -70, LAX, -70, TILT, 0, TWIST, 0)
				.kunciE(8, Easing.BACK_OUT, RAX, -85, LAX, -60, TILT, 6, TWIST, -10).kunci(20, RAX, -70, LAX, -70, TILT, 0, TWIST, 0)
				.suara(8, "entity.player.attack.sweep", 0.15F, 1.4F)
				.pasang(PropId.TOMBAK, Titik.DUA_TANGAN, 0, 0, 0, 180, 0, 0, 1.0F).bangun());
		// 40.5.10: the morning exercise of NpcPoses (WARGA), one circle per 90 ticks
		d.accept(b(GerakId.TAI_CHI_PAGI, 90).loop().mods(MOD_NAPAS_X, 0)
				.kunci(0, RAX, -74.5F, LAX, -74.5F, RAZ, 11.5F, LAZ, -11.5F, RLZ, 6.9F, LLZ, -6.9F, LEAN, 0)
				.osc(RAX, 40.1F, 89.8F).osc(LAX, -40.1F, 89.8F).osc(RAZ, 20.0F, 89.8F, 0.0F, Osilator.Bentuk.COS)
				.osc(LAZ, -20.0F, 89.8F, 0.0F, Osilator.Bentuk.COS).osc(LEAN, 2.3F, 89.8F).wajah(Wajah.MATA_TUTUP_TENANG, -1).bangun());
		d.accept(tanding(b(GerakId.TANDING_A, 60))
				.duel(DuelPose.KUDA_TENGAH, 4).duel(DuelPose.TEBAS_KANAN, 26).duel(DuelPose.KUDA_TENGAH, 6).duel(DuelPose.TANGKIS, 24)
				.suara(10, "entity.player.attack.sweep", 0.2F, 1.2F).bangun());
		d.accept(tanding(b(GerakId.TANDING_B, 60))
				.duel(DuelPose.KUDA_TENGAH, 6).duel(DuelPose.TANGKIS, 24).duel(DuelPose.KUDA_TENGAH, 4).duel(DuelPose.TEBAS_KIRI, 26)
				.suara(10, "block.wood.hit", 0.25F, 1.4F).suara(40, "entity.player.attack.sweep", 0.2F, 1.15F).bangun());
		d.accept(b(GerakId.MAIN_KEJAR, 12).loop().bendera(JALAN_OK).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, LEAN, 12, HX, -6).bangun());
		d.accept(b(GerakId.MAIN_LOMPAT, 20).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, LIFT, 0, RAX, -30, LAX, -30, RLX, 0, LLX, 0).kunciE(6, Easing.CUBIC_OUT, LIFT, 0.45F, RLX, -20, LLX, -20)
				.kunciE(12, Easing.CUBIC_IN, LIFT, 0, RLX, 0, LLX, 0).osc(RAZ, 20.0F, 20.0F).osc(LAZ, -20.0F, 20.0F)
				.suara(12, "block.grass.step", 0.15F, 1.4F).bangun());
		d.accept(b(GerakId.MAIN_GASING, 30).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, LEAN, 50, LIFT, -0.4F, RLX, -80, LLX, -80, RAX, -40, HX, 25)
				.kunciE(10, Easing.BACK_OUT, RAX, -10).kunci(30, RAX, -20)
				.pasang(PropId.GASING, Titik.DEPAN_KAKI, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.MAIN_LAYANGAN, 40).loop().bendera(LIRIK_ATAS).wajah(Wajah.MATA_SENANG, -1)
				.kunci(0, RAX, -120, HX, -30, LAX, -40).osc(RAY, 8.0F, 40.0F).osc(RAX, 4.0F, 13.0F)
				// the kite flies about 2.5 blocks up and 1.5 ahead of the raised hand, tail hanging, face to the child
				.pasang(PropId.LAYANG_LAYANG, Titik.KANAN, 0, -22.6F, -40.8F, -60, 0, 0, 1.4F).bangun());
		d.accept(b(GerakId.MAIN_LENTERA, 40).loop().bendera(LIRIK_ATAS | JALAN_OK)
				.kunci(0, RAX, -150, RAZ, 10, HX, -20).osc(RAZ, 4.0F, 40.0F).prop(PropId.LENTERA_TANGAN, 0, 0).bangun());
		d.accept(b(GerakId.MENARI_PANEN, 40).loop().wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, -150, LAX, -100, TWIST, -25, RAZ, 15, LAZ, -15).kunci(20, RAX, -100, LAX, -150, TWIST, 25)
				.osc(LIFT, 0.05F, 10.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).bangun());
		d.accept(b(GerakId.PERBAIKI_PALU, 16).loop().bendera(KERJA)
				.kunci(0, RAX, -28.6F, LAX, -50, LAY, 10, LEAN, 30, HX, 10).osc(RAX, -97.4F, 16.0F, 0.0F, Osilator.Bentuk.SIN_KUBIK)
				.suara(12, "block.wood.hit", 0.35F, 1.0F).prop(PropId.PALU, 0, 0).bangun());
		d.accept(b(GerakId.PERBAIKI_PLESTER, 30).loop().bendera(KERJA)
				.kunci(0, RAX, -80, RAY, -40, LAX, -30, LAZ, -5).kunci(15, RAY, 20)
				.prop(PropId.SENDOK_PLESTER, PropId.EMBER, 0).bangun());
		d.accept(b(GerakId.ANGKAT_PAPAN, 30).loop().bendera(JALAN_OK)
				.kunci(0, RAX, -160, RAZ, 22, LAX, 15, HZ, 6)
				// the plank lies front to back on the right shoulder, the raised hand steadies it from the side
				.pasang(PropId.PAPAN_KAYU, Titik.PUNGGUNG, -4.5F, -5.6F, -2.7F, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.GANTI_TANAH, 24).loop().bendera(KERJA | DUA_TANGAN)
				.kunci(0, RAX, -75, LAX, -75, LEAN, 0).kunciE(10, Easing.BACK_OUT, RAX, -20, LAX, -20, LEAN, 10).partikel(10, "tanah", 2, 0)
				.pasang(PropId.SEKOP, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.TANAM_ULANG, 40).loop().bendera(KAKI_PAKSA | KERJA)
				.kunci(0, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 30, HX, 25, RAX, -60, LAX, -25)
				.kunci(20, RAX, -20, LEAN, 36).partikel(20, "tanah", 2, 0).prop(PropId.BIBIT, 0, 0).bangun());
	}

	/** Sword drills: the duel source drives arms, head, body and legs (legs left to the walk while walking). */
	private static Klip.Pembangun pedang(Klip.Pembangun p) {
		return p.loop().maskSumber(HX, HY, LEAN, TWIST, RAX, RAY, RAZ, LAX, LAY, LAZ, RLX, RLZ, LLX, LLZ, RLY, LLY, LIFT)
				.prop(PropId.PEDANG_KAYU, 0, 0).wajah(Wajah.MATA_MARAH, -1);
	}

	/** Paired sparring 66/67: in step with the partner (DATA_GERAK_T0). */
	private static Klip.Pembangun tanding(Klip.Pembangun p) {
		return pedang(p).bendera(SINKRON);
	}

	// ================================================================== 38.T.8.1 themed cities

	private static void tema(Consumer<Klip> d) {
		d.accept(b(GerakId.DAYUNG_YULOH, 40).loop().bendera(SINKRON | DUA_TANGAN)
				.kunci(0, RAX, -70, LAX, -70, RAY, -30, LAY, 10, TWIST, -20, LEAN, 10).kunci(20, TWIST, 20, LEAN, 20)
				.pasang(PropId.DAYUNG_YULOH, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.TARIK_JALA, 30).loop().bendera(DUA_TANGAN)
				.kunci(0, LEAN, 25, RAX, -100, LAX, -100).kunci(15, LEAN, 5, RAX, -40, LAX, -40)
				.pasang(PropId.JALA, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.JEMUR_IKAN, 40).loop()
				.kunci(0, RAX, -150, LAX, -60, HX, -15).kunci(20, LAX, -150).prop(PropId.IKAN_TALI, 0, 0).bangun());
		d.accept(b(GerakId.IKAT_TALI, 24).loop().bendera(KAKI_PAKSA)
				.kunci(0, LIFT, -0.3F, LEAN, 35, RLX, -70, LLX, -70, RAX, -60, RAZ, 10, LAX, -60, LAZ, -10)
				.kunci(6, RAX, -45, RAZ, -10, LAX, -45, LAZ, 10).kunci(12, RAX, -60, RAZ, 10, LAX, -60, LAZ, -10)
				.kunci(18, RAX, -45, RAZ, -10, LAX, -45, LAZ, 10).prop(PropId.TALI, 0, 0).bangun());
		d.accept(b(GerakId.PALU_LUNAS, 16).loop().bendera(KERJA)
				.kunci(0, LEAN, 20, RAX, -110, LAX, -50).kunciE(5, Easing.BACK_OUT, RAX, -50).kunci(16, RAX, -110)
				.suara(5, "block.wood.hit", 0.35F, 0.9F).prop(PropId.PALU_DEMPUL, PropId.PAHAT, 0).bangun());
		d.accept(b(GerakId.TEROPONG_LAUT, 120).loop().bendera(DUA_TANGAN)
				.kunci(0, RAX, -118, RAY, -22, LAX, -118, LAY, 22, HX, -5, HY, -30).kunci(60, HY, 30)
				.pasang(PropId.TEROPONG, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.PIMPIN_UNTA, 60).loop().lapisan(Lapisan.ATAS).bendera(JALAN_OK)
				.kunci(0, RAX, 25, RAZ, 10, HY, 0).kunci(45, HY, 0).kunciE(49, Easing.CUBIC_OUT, HY, -40).kunci(55, HY, -40).kunci(59, HY, 0)
				.prop(PropId.TALI, 0, 0).bangun());
		d.accept(b(GerakId.TAWAR_BAZAR, 24).loop().bendera(BICARA)
				.kunci(0, LEAN, 8, RAX, -90, RAZ, 30, LAX, -70, HY, 0).kunci(12, RAX, -40, HY, 0).kunci(14, HY, 12).kunci(16, HY, -12)
				.kunci(18, HY, 12).kunci(20, HY, 0).osc(LAZ, 4.0F, 6.0F).bangun());
		d.accept(b(GerakId.TARI_PUTAR, 40).loop().bendera(PUTAR).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, SPIN, 9, RAX, -160, RAZ, 40, LAX, -100, LAZ, -40).kunci(20, RAX, -100, LAX, -160)
				.osc(LIFT, 0.05F, 10.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF).bangun());
		d.accept(b(GerakId.LUKIS_DINDING, 36).loop().bendera(KERJA)
				.kunci(0, HX, -15, RAX, -120, RAY, -20, LAX, -40, LAY, 15).kunci(18, RAX, -90, RAY, 20)
				.prop(PropId.KUAS_BESAR, PropId.PALET, 0).bangun());
		d.accept(b(GerakId.TIMBA_SUMUR, 60).loop().bendera(SINKRON)
				.kunci(0, RAX, -170, LAX, -170, LEAN, 10).kunci(30, RAX, -60, LAX, -60, LEAN, 0)
				.suara(30, "block.chain.step", 0.2F, 1.0F).bangun());
		d.accept(b(GerakId.TENUN, 20).loop().bendera(DUDUK | KAKI_PAKSA | SINKRON)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, RAX, -60, LAX, -40).kunci(10, RAX, -40, LAX, -60, RLX, -80)
				.suara(10, "block.wood.step", 0.12F, 1.5F).bangun());
		d.accept(b(GerakId.PETIK_TEH, 16).loop().bendera(KERJA)
				.kunci(0, LEAN, 20, RAX, -80, LAX, -40).kunciE(4, Easing.CUBIC_OUT, RAX, -95).kunci(8, RAX, -80)
				.prop(0, PropId.KERANJANG_TEH, 0).bangun());
		d.accept(b(GerakId.SEDUH_TEH, 80).sekali().bendera(DUDUK | KAKI_PAKSA)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, RAX, -30, RAZ, 0, LAX, -30, RAY, 0, LAY, 0, LEAN, 0)
				.kunci(20, RAX, -70).kunci(30, RAZ, -20).kunci(40, RAX, -70, RAZ, 0, LAX, -30).kunci(55, LAX, -60, RAZ, -15)
				.kunci(60, RAX, -60).kunci(75, RAX, -80, LAX, -80, RAY, -30, LAY, 30, LEAN, 15, RAZ, 0)
				.kunci(80, RAX, -80, LAX, -80, RAY, -30, LAY, 30, LEAN, 15)
				.partikel(30, "uap", 2, 0).prop(PropId.TEKO, PropId.CANGKIR, 0).bangun());
		d.accept(b(GerakId.NYANYI_PERAHU, 40).loop().lapisan(Lapisan.KEPALA)
				.kunci(0, HX, -10).kunci(20, HX, 0)
				.wajahPada(0, -1, Wajah.MULUT_A).wajahPada(10, -1, Wajah.MULUT_O).wajahPada(20, -1, Wajah.MULUT_A).wajahPada(30, -1, Wajah.MULUT_O)
				.bangun());
		d.accept(b(GerakId.LEPAS_LENTERA_AIR, 60).sekali().bendera(KAKI_PAKSA | DUA_TANGAN | LIRIK_BAWAH)
				.kunci(0, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 15, HX, 25, RAX, -60, LAX, -60)
				.kunciE(30, Easing.CUBIC_OUT, RAX, -30, LAX, -30).kunci(60, RAX, -30, LAX, -30)
				.pasang(PropId.LENTERA_TERATAI, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F)
				.propPada(30, PROP_SLOT_PASANG, 0).bangun());
		d.accept(b(GerakId.DAKI_PIKUL, 24).loop().bendera(JALAN_OK)
				.kunci(0, LEAN, 22, RAX, -150, RAZ, 20, LAX, -20, LIFT, 0).kunciE(6, Easing.CUBIC_OUT, LIFT, 0.04F).kunci(12, LIFT, 0, LAX, 10)
				.kunciE(18, Easing.CUBIC_OUT, LIFT, 0.04F).kunci(23, LIFT, 0).prop(0, 0, PropId.PIKULAN).bangun());
		d.accept(b(GerakId.PUTAR_KATROL, 30).loop().bendera(SINKRON)
				.kunci(0, LEAN, 15, RAX, -90, LAX, -90, RAY, 0, LAY, 0).osc(RAX, 30.0F, 30.0F).osc(LAX, 30.0F, 30.0F)
				.osc(RAY, 20.0F, 30.0F, 0.0F, Osilator.Bentuk.COS).osc(LAY, 20.0F, 30.0F, 0.0F, Osilator.Bentuk.COS)
				.suara(15, "block.chain.step", 0.15F, 1.2F).bangun());
		d.accept(b(GerakId.PETIK_HERBA_TEBING, 40).loop().bendera(KAKI_PAKSA | LIRIK_ATAS)
				.kunci(0, LEAN, -10, RAX, -160, LAX, -110, RLX, -20, LLX, 10).kunci(20, RAX, -150).osc(RAZ, 6.0F, 40.0F)
				.prop(PropId.SABIT_HERBA, 0, 0).bangun());
		d.accept(b(GerakId.SUJUD_ZIARAH, 120).sekali().bendera(KAKI_PAKSA)
				.kunci(0, RAX, 0, LAX, 0, RAY, 0, LAY, 0, RLX, 0, LLX, 0, LIFT, 0, LEAN, 0, HX, 0)
				.kunci(30, RAX, 0, LAX, 0).kunciE(40, Easing.CUBIC_OUT, RAX, -90, LAX, -90, RAY, -40, LAY, 40)
				.kunci(50, RLX, 0, LLX, 0, LIFT, 0, LEAN, 0, HX, 0)
				.kunci(65, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 70, HX, 20, RAX, -150, LAX, -150)
				.kunci(80, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 70, HX, 20, RAX, -150, LAX, -150)
				.kunci(110, RAX, 0, LAX, 0, RAY, 0, LAY, 0, RLX, 0, LLX, 0, LIFT, 0, LEAN, 0, HX, 0)
				.wajahPada(50, Wajah.MATA_TUTUP_TENANG, -1).wajahPada(105, Wajah.MATA_BUKA, -1)
				.suara(65, "block.wool.step", 0.2F, 0.9F).prop(0, 0, PropId.TAS_DUPA).bangun());
		d.accept(b(GerakId.TABUH_IKAN_KAYU, 20).loop().bendera(DUDUK | KAKI_PAKSA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, RLX, -80, LLX, -80, RLY, 35, LLY, -35, RLZ, 20, LLZ, -20, LIFT, -0.65F, RAX, -70, LAX, -40, LAY, 30)
				.kunciE(4, Easing.CUBIC_IN, RAX, -50).kunci(10, RAX, -70)
				.suara(4, "block.wood.hit", 0.3F, 1.6F).prop(PropId.PEMUKUL_KECIL, 0, 0)
				.pasang(PropId.IKAN_KAYU, Titik.DEPAN_KAKI, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.SORAK_ARENA, 20).loop().wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, -160, LAX, -30, HX, -10, LIFT, 0).kunci(10, RAX, -110, LIFT, 0.04F).bangun());
		d.accept(b(GerakId.ASAH_PISAU, 24).loop().bendera(DUDUK | KAKI_PAKSA | KERJA)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, LEAN, 30, RAX, -50, RAY, -20, LAX, -50).kunci(12, RAY, 20)
				.partikel(12, "percik", 2, 0).suara(12, "block.grindstone.use", 0.08F, 1.6F).prop(PropId.PISAU_GANDA, 0, 0).bangun());
		d.accept(b(GerakId.ANCAM, 40).sekali().bendera(ARAH_SASARAN).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MENYERINGAI)
				.kunci(0, LEAN, 0, RAX, 0, HX, 0).kunci(6, LEAN, 10).kunciE(10, Easing.BACK_OUT, RAX, -90, HX, -10)
				.kunci(30, RAX, -90, HX, -10, LEAN, 10).kunci(40, RAX, 0, HX, 0, LEAN, 0).bangun());
		d.accept(b(GerakId.HITUNG_KOIN_GELAP, 30).loop().bendera(DUDUK | KAKI_PAKSA | LIRIK_BAWAH)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, RAX, -60, LAX, -50, HY, 0).kunciE(3, Easing.TANGGA, RAX, -50)
				.kunci(10, RAX, -60).kunciE(13, Easing.TANGGA, RAX, -50).kunci(20, RAX, -60).kunciE(23, Easing.TANGGA, RAX, -50)
				.kunci(25, HY, 0).kunciE(27, Easing.CUBIC_OUT, HY, 30).kunciE(29, Easing.CUBIC_OUT, HY, -30)
				.prop(0, PropId.KANTONG_UANG, 0).bangun());
		d.accept(b(GerakId.BERLUTUT_RANTAI, 80).loop().bendera(KAKI_PAKSA).mods(MOD_GEMETAR, 0.8F)
				.wajah(Wajah.MATA_SEDIH, Wajah.MULUT_MENGGIGIT_BIBIR)
				.kunci(0, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 15, RAX, 20, LAX, 20, RAZ, 15, LAZ, -15, HX, 30)
				.pasang(PropId.RANTAI_TANGAN, Titik.PUNGGUNG, 0, 8, 1, 0, 0, 0, 0.8F).bangun());
		d.accept(b(GerakId.SEMEDI_DARAH, 80).loop().bendera(DUDUK | BERSILA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, LIFT, 0.03F).kunci(40, LIFT, 0.045F).osc(HY, 10.0F, 80.0F)
				.partikel(0, "darah_qi", 2, 0x8A1020).partikel(40, "darah_qi", 2, 0x8A1020).bangun());
		d.accept(b(GerakId.PIKUL_TANDU, 30).loop().bendera(SINKRON | JALAN_OK)
				.kunci(0, RAX, -150, RAZ, 20, LAX, -150, LAZ, -20, LEAN, 3).bangun());
		d.accept(b(GerakId.TABUH_GENDERANG_PERAHU, 12).loop().bendera(SINKRON)
				.kunci(0, LEAN, 10, RAX, -140, LAX, -40).kunciE(6, Easing.CUBIC_IN, RAX, -40, LAX, -140)
				.suara(0, "block.note_block.basedrum", 0.35F, 0.7F).suara(6, "block.note_block.basedrum", 0.35F, 0.75F)
				.prop(PropId.PEMUKUL, PropId.PEMUKUL, 0).bangun());
	}

	private KatalogTempat() {
	}
}

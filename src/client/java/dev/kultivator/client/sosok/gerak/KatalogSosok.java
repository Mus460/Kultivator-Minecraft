package dev.kultivator.client.sosok.gerak;

import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

import static dev.kultivator.client.sosok.gerak.Klip.*;

/**
 * DESIGN 40's own clips 108-176 (40.5.11-40.5.13): the idle stances (from {@code NpcPoses} plus new ones for women,
 * elders, children and traits), the cultivators' and disciples' clips (meditation breath, qi circulation, hand seals,
 * standing post, palm drills from the cast poses, sword drills from the duel poses, alchemy, talismans, formations,
 * bows to the master, breakthroughs, wounds, tears, music, wine and pills), the catalog ids of the existing function
 * poses 150-164 (no keys: the old poses play through the disciple's activity), the social gestures 165-171 and the
 * autonomous-disciple clips 172-176 with the activity map 40-56 (and safe defaults for 39F's 57-60, which 39F may
 * override through {@link GerakKatalog#petakanAktivitas}).
 */
final class KatalogSosok {

	static void daftarkan(Consumer<Klip> d) {
		sikap(d);
		kultivasi(d);
		kerjaMurid(d);
		perasaan(d);
		fungsi(d);
		sosial(d);
		mandiri(d);
	}

	/** The activity map of 40.5.13 (and defaults for 57-60). */
	static void petakan() {
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_LATIH_JURUS, GerakId.LATIH_JURUS, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_ALKIMIA, GerakId.KIPAS_TUNGKU, GerakId.V_KIPAS_TUNGKU_DUDUK);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_JIMAT, GerakId.TULIS_JIMAT, GerakId.V_TULIS_JIMAT_DUDUK);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_TANDING, GerakId.TANDING_A, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_MENGAJAR, GerakId.MENGAJAR, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_TUTUP_DIRI, GerakId.MEDITASI_NAPAS, GerakId.V_MEDITASI_TUTUP_DIRI);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_BERKEMAS, GerakId.BERKEMAS, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_BAWA, GerakId.JALAN_BAWA, GerakId.V_JALAN_BAWA_PUNGGUNG);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_MAKAN, GerakId.MAKAN, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_TAWAR, GerakId.TAWAR, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_LIHAT_LAPAK, GerakId.LIHAT_BARANG, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_ISTIRAHAT, GerakId.BERSANDAR, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_TERBANG_JAUH, GerakId.BERDIRI_DI_PEDANG, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_MERENUNG, GerakId.TATAP_LANGIT, GerakId.V_TATAP_RENUNG);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_SERAP_BATU, GerakId.GENGGAM_BATU_ROH, GerakId.V_GENGGAM_TERBUKA);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_JAGA_TEROBOSAN, GerakId.SIKAP_LIPAT_TANGAN, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_MEDITASI_MANDIRI, GerakId.MEDITASI_NAPAS, 0);
		// 39F (feuds) owns 57-60 and may remap them to its 250-299 clips; until then they show plain body language
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_CEKCOK, GerakId.BERDEBAT, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_TANTANG, GerakId.SIKAP_BERKACAK, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_TERLUKA, GerakId.LENGAN_DIGENDONG, 0);
		GerakKatalog.petakanBawaan(MandiriKegiatan.ACT_SUMPAH, GerakId.SIKAP_DOA, 0);
	}

	private static Klip.Pembangun b(int id, int durasi) {
		return Klip.baru(id, GerakId.namaAtauId(id), durasi);
	}

	/** The hands-behind-the-back keys (108), reused by 119 and the musing variant. */
	private static Klip.Pembangun tanganBelakang(Klip.Pembangun p, int t) {
		return p.kunci(t, RAX, 28.6F, RAZ, -12.6F, LAX, 28.6F, LAZ, 12.6F);
	}

	// ================================================================== 40.5.11 idle stances 108-121

	private static void sikap(Consumer<Klip> d) {
		d.accept(tanganBelakang(b(GerakId.SIKAP_TANGAN_BELAKANG, 80).loop().lapisan(Lapisan.ATAS).bendera(JALAN_OK), 0).bangun());
		d.accept(b(GerakId.SIKAP_LIPAT_TANGAN, 80).loop().lapisan(Lapisan.ATAS)
				.kunci(0, RAX, -57.3F, RAY, -44.7F, RAZ, 3.4F, LAX, -54.4F, LAY, 44.7F, LAZ, -3.4F).bangun());
		d.accept(b(GerakId.SIKAP_TANGAN_LENGAN, 80).loop().lapisan(Lapisan.ATAS).bendera(JALAN_OK)
				.kunci(0, RAX, -28.6F, RAY, -28.6F, LAX, -28.6F, LAY, 28.6F)
				.varian(GerakId.V_TANGAN_LENGAN_ABADI, v -> v.kunci(0, RAX, -41.3F, RAY, -28.6F, LAX, -41.3F, LAY, 28.6F))
				.bangun());
		d.accept(b(GerakId.SIKAP_TOMBAK, 60).loop().lapisan(Lapisan.ATAS)
				.kunci(0, RAX, -24, RAZ, 3.4F, LAX, -6.9F, LAZ, 6.9F).osc(HY, 25, 60).bangun());
		d.accept(b(GerakId.SIKAP_GAGANG, 80).loop().lapisan(Lapisan.ATAS)
				.kunci(0, LAX, -21.8F, LAY, 18.3F, LAZ, 9.2F).bangun());
		d.accept(b(GerakId.SIKAP_SANTAI_L, 240).loop()
				.kunci(0, RLZ, 3.5F, LLZ, -3.5F, RAZ, 4, LAZ, -4, BZ, 1).kunci(100, BZ, 1).kunci(120, BZ, -1).kunci(220, BZ, -1).bangun());
		d.accept(b(GerakId.SIKAP_SANTAI_P, 240).loop()
				.kunci(0, RAX, -22, RAY, -26, LAX, -22, LAY, 26, LLZ, -4, RLZ, 0, BZ, 2.5F, HZ, -2)
				.kunci(100, LLZ, -4, RLZ, 0, BZ, 2.5F, HZ, -2).kunci(120, LLZ, 0, RLZ, 4, BZ, -2.5F, HZ, 2)
				.kunci(220, LLZ, 0, RLZ, 4, BZ, -2.5F, HZ, 2).bangun());
		d.accept(b(GerakId.SIKAP_TUA, 100).loop().bendera(JALAN_OK)
				.kunci(0, LEAN, 18, HX, -10, RAX, 28, RAZ, -10, LAX, 28, LAZ, 10)
				.varian(GerakId.V_TUA_TONGKAT, v -> v.tanpaBendera(JALAN_OK).mods(MOD_GEMETAR, 0.6F)
						.kunci(0, LEAN, 18, HX, -10, RAX, -35, RAZ, 10, LAX, -35, LAY, 25).prop(PropId.TONGKAT, 0, 0))
				.bangun());
		d.accept(b(GerakId.SIKAP_ANAK, 120).loop()
				.kunci(0, LIFT, 0).kunci(40, LIFT, 0).kunciE(44, Easing.CUBIC_OUT, LIFT, 0.06F).kunci(48, LIFT, 0)
				.osc(HY, 25, 60).osc(RAZ, 10, 30).osc(LAZ, -10, 30).bangun());
		d.accept(b(GerakId.SIKAP_BERKACAK, 80).loop()
				.kunci(0, RAX, 8, RAY, 20, RAZ, 25, LAX, 8, LAY, -20, LAZ, -25, LEAN, -4, HX, -4)
				// women: one fist on the hip, weight on the other leg, chin tilted
				.varian(SY_P, v -> v.kunci(0, RAX, 8, RAY, 20, RAZ, 25, LAX, -10, LAZ, -4, LLZ, -3, BZ, 3, HZ, -3, HX, -4))
				.bangun());
		d.accept(b(GerakId.SIKAP_TUNDUK, 80).loop().bendera(LIRIK_BAWAH)
				.kunci(0, HX, 18, LEAN, 6, RAX, -15, RAY, -30, LAX, -15, LAY, 30).bangun());
		d.accept(tanganBelakang(b(GerakId.SIKAP_ANGKUH, 80).loop(), 0).kunci(0, HX, -12, LEAN, -5).bangun());
		d.accept(b(GerakId.SIKAP_DOA, 80).loop().bendera(JALAN_OK | MATA_SETENGAH_WAKTU)
				.kunci(0, RAX, -70, RAY, -38, LAX, -70, LAY, 38, HX, 10).bangun());
		d.accept(b(GerakId.SIKAP_TANGAN_DEPAN, 120).loop()
				.kunci(0, RAX, -30, RAY, -32, LAX, -30, LAY, 32, BZ, 1.5F).bangun());
	}

	// ================================================================== 40.5.12 cultivation

	private static void kultivasi(Consumer<Klip> d) {
		d.accept(b(GerakId.MEDITASI_NAPAS, 120).loop().bendera(DUDUK | BERSILA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, RAX, -40, RAY, -38, LAX, -40, LAY, 38, BOB, 0, HX, 2).kunci(60, BOB, 0.3F, HX, -2)
				.varian(GerakId.V_MEDITASI_LUTUT, v -> v.kunci(0, BOB, 0, HX, 2).kunci(60, BOB, 0.3F, HX, -2))
				.varian(GerakId.V_MEDITASI_TUTUP_DIRI, v -> v.durasi(160)
						.kunci(0, RAX, -40, RAY, -38, LAX, -40, LAY, 38, BOB, 0, HX, 2, NAPAS, 0.6F).kunci(80, BOB, 0.3F, HX, -2)
						.kait(0, "mandiri:segel"))
				.bangun());
		d.accept(b(GerakId.SIRKULASI_QI, 160).loop().bendera(DUDUK | BERSILA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, RAX, -40, LAX, -40, RAY, -38, LAY, 38, RAZ, 0, LAZ, 0, LIFT, 0)
				.kunciE(40, Easing.CUBIC_OUT, RAX, -95, LAX, -95, RAY, -40, LAY, 40)
				.kunci(80, RAZ, 10, LAZ, -10, LIFT, 0.03F).kunci(120, RAX, -60, LAX, -60, RAZ, 5, LAZ, -5, LIFT, 0.01F)
				.partikel(40, "qi_tipis", 6, -1).partikel(100, "qi_tipis", 4, -1).bangun());
		// six hand seals, each snapped into in 2-3 ticks and held
		d.accept(b(GerakId.SEGEL_TANGAN, 70).sekali()
				.kunci(0, RAX, 0, LAX, 0, RAY, 0, LAY, 0, RAZ, 0, LAZ, 0, HX, 0)
				.kunciE(3, Easing.CUBIC_OUT, RAX, -80, LAX, -80, RAY, -40, LAY, 40, HX, 5)
				.kunci(10, RAX, -80, LAX, -80, RAY, -40, LAY, 40, RAZ, 0, LAZ, 0)
				.kunciE(12, Easing.CUBIC_OUT, RAX, -95, LAX, -70, RAY, -20, LAY, 50)
				.kunci(20, RAX, -95, LAX, -70, RAY, -20, LAY, 50, RAZ, 0, LAZ, 0)
				.kunciE(22, Easing.CUBIC_OUT, RAX, -80, LAX, -80, RAY, -30, LAY, 30, RAZ, 15, LAZ, -15)
				.kunci(30, RAX, -80, LAX, -80, RAY, -30, LAY, 30, RAZ, 15, LAZ, -15)
				.kunciE(32, Easing.CUBIC_OUT, RAX, -110, LAX, -110, RAY, -30, LAY, 30, RAZ, 0, LAZ, 0)
				.kunci(40, RAX, -110, LAX, -110, RAY, -30, LAY, 30)
				.kunciE(42, Easing.CUBIC_OUT, RAX, -70, LAX, -90, RAY, -50, LAY, 20)
				.kunci(50, RAX, -70, LAX, -90, RAY, -50, LAY, 20)
				.kunciE(52, Easing.CUBIC_OUT, RAX, -80, LAX, -80, RAY, -40, LAY, 40)
				.kunci(60, RAX, -80, LAX, -80, RAY, -40, LAY, 40, HX, 5)
				.kunci(70, RAX, 0, LAX, 0, RAY, 0, LAY, 0, HX, 0)
				.suara(12, "block.amethyst_block.step", 0.12F, 1.6F).suara(32, "block.amethyst_block.step", 0.12F, 1.7F)
				.suara(60, "block.amethyst_block.chime", 0.3F, 1.2F).partikel(60, "qi_tipis", 5, -1)
				.wajahPada(52, Wajah.MATA_KILAT, -1).wajahPada(66, Wajah.MATA_BUKA, -1).bangun());
		d.accept(b(GerakId.TIANG_BERDIRI, 120).loop().bendera(KAKI_PAKSA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP_TENANG, -1)
				.kunci(0, RLX, -15, LLX, -15, RLZ, 6, LLZ, -6, LIFT, -0.08F, LEAN, 4,
						RAX, -70, RAY, -30, RAZ, 15, LAX, -70, LAY, 30, LAZ, -15, NAPAS, 1.5F).bangun());
		d.accept(b(GerakId.TELAPAK_LATIHAN, 80).loop().bendera(KAKI_PAKSA)
				.cast(Pose.PALM_THRUST, 0, 20, false).cast(Pose.TWIN_PALM, 20, 40, false).cast(Pose.PALM_THRUST, 40, 60, true)
				.cast(Pose.SEAL, 60, 80, false)
				.kunci(0, TWIST, 0, RLZ, 20, LLZ, -20, RLX, -15, LLX, -15, LIFT, -0.15F).kunciE(8, Easing.BACK_OUT, TWIST, -15)
				.kunci(20, TWIST, 0).kunci(40, TWIST, 0).kunciE(48, Easing.BACK_OUT, TWIST, 15).kunci(60, TWIST, 0)
				.suara(8, "entity.player.attack.sweep", 0.2F, 1.4F).suara(28, "entity.player.attack.sweep", 0.2F, 1.3F)
				.suara(48, "entity.player.attack.sweep", 0.2F, 1.4F).bangun());
	}

	// ================================================================== 40.5.12 disciples' crafts

	private static void kerjaMurid(Consumer<Klip> d) {
		d.accept(b(GerakId.ASAH_PEDANG, 24).loop().bendera(DUDUK | KAKI_PAKSA | KERJA | LIRIK_BAWAH)
				.kunci(0, RLX, -80, LLX, -80, RLY, 35, LLY, -35, RLZ, 20, LLZ, -20, LIFT, -0.65F,
						LEAN, 20, HX, 25, RAX, -50, RAY, -20, LAX, -55, LAY, 15)
				.kunci(12, RAY, 20).partikel(12, "kilau", 1, 0)
				.pasang(PropId.KAIN, Titik.KANAN, 0, 0, 0, 0, 0, 0, 0.4F).pasang(PropId.PEDANG_KAYU, Titik.KIRI, 0, 0, 0, 70, 0, 60, 0.9F).bangun());
		d.accept(b(GerakId.TEBAS_UDARA, 30).sekali().wajah(Wajah.MATA_MARAH, -1)
				.maskSumber(HX, HY, LEAN, TWIST, RAX, RAY, RAZ, LAX, LAY, LAZ, RLX, RLZ, LLX, LLZ, LIFT)
				.duel(DuelPose.HUNUS, 8).duel(DuelPose.TEBAS_ATAS, 10).duel(DuelPose.SARUNG, 12)
				.suara(14, "entity.player.attack.sweep", 0.25F, 1.2F).prop(PropId.PEDANG_KAYU, 0, 0).bangun());
		d.accept(b(GerakId.SUJUD_GURU, 110).sekali().bendera(KAKI_PAKSA)
				.kunci(0, RLX, 0, LLX, 0, LIFT, 0, LEAN, 0, HX, 0, RAX, 0, LAX, 0)
				.kunciE(10, Easing.CUBIC_OUT, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 15, HX, 20)
				.kunci(15, LEAN, 10, RAX, -20, LAX, -20).kunci(23, LEAN, 62, RAX, -100, LAX, -100).kunci(30, LEAN, 10, RAX, -20, LAX, -20)
				.kunci(45, LEAN, 10, RAX, -20, LAX, -20).kunci(53, LEAN, 62, RAX, -100, LAX, -100).kunci(60, LEAN, 10, RAX, -20, LAX, -20)
				.kunci(75, LEAN, 10, RAX, -20, LAX, -20).kunci(83, LEAN, 62, RAX, -100, LAX, -100).kunci(90, LEAN, 10, RAX, -20, LAX, -20)
				.kunci(100, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 15, HX, 20, RAX, 0, LAX, 0)
				.kunci(110, RLX, 0, LLX, 0, LIFT, 0, LEAN, 0, HX, 0)
				.wajahPada(18, Wajah.MATA_TUTUP, -1).wajahPada(30, Wajah.MATA_BUKA, -1).wajahPada(48, Wajah.MATA_TUTUP, -1)
				.wajahPada(60, Wajah.MATA_BUKA, -1).wajahPada(78, Wajah.MATA_TUTUP, -1).wajahPada(90, Wajah.MATA_BUKA, -1)
				.suara(30, "block.wool.step", 0.2F, 0.9F).suara(60, "block.wool.step", 0.2F, 0.9F).suara(90, "block.wool.step", 0.2F, 0.9F)
				.bangun());
		d.accept(b(GerakId.KIPAS_TUNGKU, 20).loop().bendera(KAKI_PAKSA | KERJA)
				.kunci(0, LIFT, -0.35F, LEAN, 30, RLX, -70, LLX, -70, RAX, -70, LAX, -40, HX, 15).osc(RAZ, 20, 10)
				.partikel(0, "asap", 1, 0).partikel(10, "asap", 1, 0).prop(PropId.KIPAS_TUNGKU, 0, 0)
				// ACT 41: cross-legged by the small cauldron, fanning with the left, sprinkling herbs with the right
				.varian(GerakId.V_KIPAS_TUNGKU_DUDUK, v -> v.durasi(80).tanpaBendera(KAKI_PAKSA).bendera(DUDUK | BERSILA)
						.kunci(0, RAX, -60, RAY, 0, LAX, -60, HX, 20).kunci(40, RAX, -80, RAY, -10).kunciE(46, Easing.BACK_OUT, RAX, -60, RAY, 0)
						.osc(LAZ, 20, 17).prop(0, PropId.KIPAS_TUNGKU, 0)
						.kait(0, "mandiri:asap").kait(10, "mandiri:asap").kait(20, "mandiri:asap").kait(30, "mandiri:asap")
						.kait(40, "mandiri:asap").kait(50, "mandiri:asap").kait(60, "mandiri:asap").kait(70, "mandiri:asap"))
				.bangun());
		d.accept(b(GerakId.RACIK_HERBA, 30).loop().bendera(KERJA)
				.kunci(0, RAX, -60, RAY, 0, LAX, -40, LAY, 20, LEAN, 15, HX, 20).kunci(10, RAX, -95, RAY, -10)
				.kunciE(14, Easing.BACK_OUT, RAX, -75).partikel(15, "minecraft:flame", 2, 0)
				.prop(PropId.HERBA, 0, 0).pasang(PropId.KERANJANG, Titik.KIRI, 0, 0, 0, 0, 0, 0, 0.6F).bangun());
		d.accept(b(GerakId.GULUNG_PIL, 16).loop().bendera(KERJA | LIRIK_BAWAH | DUA_TANGAN)
				.kunci(0, RAX, -75, RAY, -35, LAX, -75, LAY, 35, HX, 25).osc(RAZ, 6, 8).osc(LAZ, -6, 8)
				.pasang(PropId.PIL, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F).bangun());
		d.accept(b(GerakId.PASANG_BENDERA, 40).sekali()
				.kunci(0, RAX, -150, LEAN, 0, LIFT, 0).kunciE(12, Easing.BACK_OUT, RAX, -40, LEAN, 30, LIFT, -0.2F)
				.kunci(30, RAX, -20, LEAN, 0, LIFT, 0).kunci(40, RAX, 0)
				.suara(12, "block.wood.place", 0.4F, 0.9F).partikel(12, "qi_tipis", 6, -1)
				.prop(PropId.BENDERA_FORMASI, 0, 0).bangun());
		d.accept(b(GerakId.TULIS_JIMAT, 30).loop().bendera(KERJA | LIRIK_BAWAH)
				.kunci(0, LEAN, 15, HX, 25, RAX, -55, RAY, -15, RAZ, 0, LAX, -50).kunci(8, RAY, 10).kunci(14, RAX, -70)
				.kunci(20, RAY, -5).kunci(24, RAZ, 15).partikel(20, "glif", 3, 0xE8C860)
				.prop(PropId.KUAS, 0, 0).pasang(PropId.KERTAS_JIMAT, Titik.KIRI, 0, 0, 0, 50, 0, 0, 1.0F)
				// ACT 42: seated at the low table, the zig-zag a fifth wider
				.varian(GerakId.V_TULIS_JIMAT_DUDUK, v -> v.bendera(DUDUK | BERSILA)
						.kunci(0, LEAN, 25, HX, 28, RAX, -55, RAY, -18, RAZ, 0, LAX, -50).kunci(8, RAY, 12).kunci(14, RAX, -72)
						.kunci(20, RAY, -6).kunci(24, RAZ, 18).partikel(20, "glif", 3, 0xE8C860).kait(29, "mandiri:jimat_jadi"))
				.bangun());
		d.accept(b(GerakId.TEMPEL_JIMAT, 20).sekali()
				.kunci(0, RAX, -100, TWIST, 0, HX, 0).kunciE(6, Easing.BACK_OUT, RAX, -85, TWIST, 10, HX, -5)
				.kunci(20, RAX, 0, TWIST, 0, HX, 0).suara(6, "item.book.page_turn", 0.3F, 1.2F).prop(PropId.KERTAS_JIMAT, 0, 0).bangun());
		d.accept(b(GerakId.PERIKSA_NADI, 80).loop().bendera(DUDUK | KAKI_PAKSA).wajah(Wajah.MATA_MENGANTUK, -1)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, RAX, -55, RAY, -20, LAX, -45, HX, 20).kunci(36, HX, 20)
				.kunci(40, HX, 26).kunci(44, HX, 20).bangun());
		d.accept(b(GerakId.BERI_MAKAN_BINATANG, 40).loop().bendera(KAKI_PAKSA).wajah(-1, Wajah.MULUT_SENYUM)
				.kunci(0, LIFT, -0.35F, LEAN, 35, RLX, -70, LLX, -70, RAX, -60, HX, 25).kunci(15, RAX, -40)
				.prop(PropId.HERBA, 0, 0).bangun());
		d.accept(b(GerakId.MENGAJAR, 120).loop().bendera(BICARA)
				.kunci(0, LAX, 28.6F, LAZ, 12.6F, RAX, -50, RAZ, 10, HX, 3).osc(RAX, 15, 30).osc(HX, 3, 40).osc(HY, 20, 120)
				.varian(GerakId.V_MENGAJAR_TETUA, v -> v.warisi().prop(PropId.CAP_SEKTE, 0, 0))
				.varian(SY_TETUA, v -> v.warisi().prop(PropId.KEBUT, 0, 0))
				.prop(PropId.GULUNGAN, 0, 0).bangun());
		d.accept(b(GerakId.MAIN_SERULING, 80).loop().wajah(-1, Wajah.MULUT_MENIUP)
				.cast(Pose.FLUTE, 0, 80, false).kunci(0, HZ, 8)
				.partikel(0, "minecraft:note", 1, 0).partikel(20, "minecraft:note", 1, 0).partikel(40, "minecraft:note", 1, 0)
				.partikel(60, "minecraft:note", 1, 0)
				// across the lips: turned sideways from the carrying grip (which hangs it down the forearm)
				.pasang(PropId.SERULING, Titik.KANAN, 0, -3, 1, 180, 0, 90, 1.0F).bangun());
		d.accept(b(GerakId.MAIN_GUQIN, 24).loop().bendera(DUDUK | BERSILA | LIRIK_BAWAH)
				.kunci(0, LEAN, 15, HX, 25, RAX, -45, RAY, -10, LAX, -45, LAY, 15).osc(RAZ, 5, 6).osc(LAY, 6, 24)
				.suara(0, "block.note_block.harp", 0.25F, -1).suara(6, "block.note_block.harp", 0.25F, -1)
				.suara(12, "block.note_block.harp", 0.25F, -1).suara(18, "block.note_block.harp", 0.25F, -1)
				.pasang(PropId.GUQIN, Titik.PANGKUAN, 0, 0, 0, 90, 90, 0, 1.0F).bangun());
		d.accept(b(GerakId.MINUM_ARAK, 40).sekali()
				.kunci(0, RAX, -40, RAY, 0, HX, 0).kunciE(10, Easing.CUBIC_OUT, RAX, -150, RAY, -20, HX, -35)
				.kunci(30, RAX, -150, RAY, -20, HX, -35).kunci(40, RAX, 0, RAY, 0, HX, 0)
				.wajahPada(10, Wajah.MATA_TUTUP, Wajah.MULUT_MENIUP).wajahPada(30, Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.suara(14, "entity.generic.drink", 0.3F, 1.0F).prop(PropId.LABU_ARAK, 0, 0)
				// civilian women drink from a small cup behind the raised left sleeve
				.varian(SY_P | SY_SIPIL, v -> v.prop(PropId.CAWAN_ARAK, 0, 0)
						.kunci(0, RAX, -40, RAY, 0, HX, 0, LAX, 0, LAY, 0)
						.kunciE(10, Easing.CUBIC_OUT, RAX, -100, RAY, -28, HX, -18, LAX, -92, LAY, 40)
						.kunci(30, RAX, -100, RAY, -28, HX, -18, LAX, -92, LAY, 40).kunci(40, RAX, 0, RAY, 0, HX, 0, LAX, 0, LAY, 0)
						.wajahPada(10, Wajah.MATA_TUTUP, -1).wajahPada(30, Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
						.suara(14, "entity.generic.drink", 0.22F, 1.2F))
				.bangun());
		d.accept(b(GerakId.MAKAN_PIL, 24).sekali()
				.kunci(0, RAX, -60, HX, 0).kunciE(8, Easing.CUBIC_OUT, RAX, -100, HX, -15).kunci(10, HX, 5).kunci(24, RAX, 0, HX, 0)
				.wajahPada(10, -1, Wajah.MULUT_MAKAN).wajahPada(16, -1, Wajah.MULUT_DIAM)
				.suara(10, "entity.generic.eat", 0.2F, 1.4F).prop(PropId.PIL, 0, 0).bangun());
		d.accept(b(GerakId.GENGGAM_BATU_ROH, 80).loop().bendera(DUDUK | BERSILA | MATA_TUTUP | DUA_TANGAN)
				.wajah(Wajah.MATA_TUTUP_TENANG, -1).kunci(0, RAX, -45, RAY, -35, LAX, -45, LAY, 35)
				.partikel(0, "qi_masuk", 3, 0x5FE0A0).partikel(20, "qi_masuk", 3, 0x5FE0A0).partikel(40, "qi_masuk", 3, 0x5FE0A0)
				.partikel(60, "qi_masuk", 3, 0x5FE0A0).pasang(PropId.BATU_ROH, Titik.DUA_TANGAN, 0, 0, 0, 0, 0, 0, 1.0F)
				.varian(GerakId.V_GENGGAM_TERBUKA, v -> v.tanpaProp().tanpaBendera(DUA_TANGAN)
						.kunci(0, RAX, -50, RAY, -20, RAZ, 20, LAX, -50, LAY, 20, LAZ, -20).kait(0, "mandiri:batu_orbit"))
				.bangun());
	}

	// ================================================================== 40.5.12 feelings and states

	private static void perasaan(Consumer<Klip> d) {
		Consumer<Klip.Pembangun> tenang = v -> v.wajah(Wajah.MATA_TUTUP_TENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, 0, LAX, 0, HX, 0).kunci(10, RAX, -70, RAY, -38, LAX, -70, LAY, 38, HX, -20)
				.kunci(50, RAX, -70, RAY, -38, LAX, -70, LAY, 38, HX, -20).kunci(60, RAX, 0, RAY, 0, LAX, 0, LAY, 0, HX, 0);
		Consumer<Klip.Pembangun> sombong = v -> v.mods(MOD_DETAK, 0).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, 0, LAX, 0, LEAN, 0, HX, 0)
				.kunci(8, RAX, -57.3F, RAY, -44.7F, RAZ, 3.4F, LAX, -54.4F, LAY, 44.7F, LAZ, -3.4F, LEAN, -8, HX, -15)
				.kunci(52, RAX, -57.3F, RAY, -44.7F, RAZ, 3.4F, LAX, -54.4F, LAY, 44.7F, LAZ, -3.4F, LEAN, -8, HX, -15)
				.kunci(60, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, LAZ, 0, LEAN, 0, HX, 0)
				.suara(10, "entity.villager.celebrate", 0.3F, 1.0F);
		d.accept(b(GerakId.TEROBOSAN_BERHASIL, 60).sekali().wajah(Wajah.MATA_KILAT, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, 0, LAX, 0, RAZ, 0, LAZ, 0, HX, 0, LIFT, 0)
				.kunciE(6, Easing.BACK_OUT, RAX, -170, LAX, -170, RAZ, 30, LAZ, -30, HX, -25)
				.kunci(20, RAX, -150, LAX, -150).kunci(24, RAX, -120, LAX, -120).kunci(28, RAX, -150, LAX, -150)
				.kunci(32, RAX, -120, LAX, -120).kunci(36, RAX, -150, LAX, -150).kunci(40, LIFT, 0.2F).kunci(44, LIFT, 0)
				.kunci(60, RAX, 0, LAX, 0, RAZ, 0, LAZ, 0, HX, 0, LIFT, 0)
				.wajahPada(44, Wajah.MATA_SENANG, -1)
				.varian(GerakId.V_TEROBOSAN_TENANG, tenang).varian(GerakId.V_TEROBOSAN_SOMBONG, sombong)
				.varian(Klip.sifat(Sifat.TENANG.ordinal()), tenang).varian(Klip.sifat(Sifat.SOMBONG.ordinal()), sombong)
				.bangun());
		d.accept(b(GerakId.TEROBOSAN_GAGAL, 70).tahan().bendera(KAKI_PAKSA).mods(MOD_GUNCANG_MUATAN, 1.2F)
				.wajah(Wajah.MATA_SAKIT, -1)
				.kunci(0, LEAN, 25, RAX, -70, RAY, -45, HX, 15, RLX, 0, LLX, 0, LIFT, 0).kunci(14, LEAN, 30, HX, 22)
				.kunciE(20, Easing.CUBIC_IN, RLX, -90, LLX, 60, LIFT, -0.4F, LEAN, 32)
				.wajahPada(10, -1, Wajah.MULUT_BATUK_DARAH).partikel(10, "darah", 4, 0)
				.suara(10, "entity.player.hurt", 0.25F, 0.8F).suara(20, "entity.player.big_fall", 0.2F, 1.0F).bangun());
		d.accept(b(GerakId.PINCANG, 40).loop().lapisan(Lapisan.JALAN).bendera(JALAN_OK).wajah(Wajah.MATA_SAKIT, -1)
				.kunci(0, LEAN, 6, LAX, -20, LAY, 20, HX, 8).bangun());
		d.accept(b(GerakId.LENGAN_DIGENDONG, 40).loop().lapisan(Lapisan.ATAS).bendera(JALAN_OK)
				.kunci(0, LAX, -60, LAY, 40, LAZ, -5).bangun());
		d.accept(b(GerakId.MENGUAP_PEREGANGAN, 50).sekali()
				.kunci(0, RAX, 0, LAX, 0, LEAN, 0, HX, 0, HY, 0).kunci(15, RAX, -170, LAX, -170, LEAN, -10, HX, -20)
				.kunci(22, RAX, -170, LAX, -170, LEAN, -10, HX, -20).kunci(30, RAX, 0, LAX, 0, LEAN, 0, HX, 0)
				.kunci(36, HY, 0).kunci(40, HY, 10).kunci(44, HY, -10).kunci(48, HY, 0)
				.wajahPada(5, Wajah.MATA_TUTUP, Wajah.MULUT_A).wajahPada(28, Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				// women stretch one arm and cover the yawn with the other hand
				.varian(SY_P, v -> v
						.kunci(0, RAX, 0, RAY, 0, LAX, 0, LEAN, 0, HX, 0, HY, 0)
						.kunci(12, RAX, -122, RAY, -48, LAX, -165, LEAN, -4, HX, -15).kunci(26, RAX, -122, RAY, -48, LAX, -165, LEAN, -4, HX, -15)
						.kunci(34, RAX, 0, RAY, 0, LAX, 0, LEAN, 0, HX, 0)
						.kunci(36, HY, 0).kunci(40, HY, 10).kunci(44, HY, -10).kunci(48, HY, 0)
						.wajahPada(5, Wajah.MATA_TUTUP, Wajah.MULUT_A).wajahPada(30, Wajah.MATA_BUKA, Wajah.MULUT_DIAM))
				.varian(GerakId.V_MENGUAP_RAPIKAN, v -> v.durasi(32)
						.kunci(0, LAX, 0, LAY, 0, RAX, 0).kunci(6, LAX, -60, LAY, 40, RAX, -30).kunci(26, LAX, -60, LAY, 40, RAX, -30)
						.kunci(32, LAX, 0, LAY, 0, RAX, 0).osc(LAX, 3, 6))
				.bangun());
		d.accept(b(GerakId.MENANGIS, 40).loop().wajah(Wajah.MATA_SEDIH, Wajah.MULUT_SEDIH)
				.kunci(0, RAX, -120, RAY, -30, LAX, -120, LAY, 30, LEAN, 15, HX, 20).osc(BAHU, 0.5F, 10)
				// children rub their eyes with both fists and wail; women dab the tears with the right sleeve
				.varian(SY_ANAK, v -> v.durasi(30).wajah(Wajah.MATA_SEDIH, Wajah.MULUT_A)
						.kunci(0, RAX, -125, RAY, -25, LAX, -125, LAY, 25, HX, 10, LEAN, 6)
						.osc(RAZ, 8, 15).osc(LAZ, 8, 15, Mth.PI, Osilator.Bentuk.SIN).osc(BAHU, 0.8F, 8))
				.varian(SY_P, v -> v.durasi(60)
						.kunci(0, RAX, -60, RAY, -10, LAX, -30, LAY, 25, HX, 15, LEAN, 8)
						.kunci(12, RAX, -118, RAY, -30).kunci(30, RAX, -118, RAY, -30).kunci(42, RAX, -60, RAY, -10)
						.osc(BAHU, 0.4F, 12).osc(HZ, 3, 60))
				.bangun());
	}

	// ================================================================== 150-164 function poses (no keys)

	private static void fungsi(Consumer<Klip> d) {
		for (int id = GerakId.FUNGSI_FIRST; id <= GerakId.FUNGSI_LAST; id++) {
			d.accept(b(id, 1).loop().fungsi().bangun());
		}
	}

	// ================================================================== 165-171 social gestures

	private static void sosial(Consumer<Klip> d) {
		d.accept(b(GerakId.TEPUK_TANGAN, 10).loop().wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -70, LAX, -70, RAY, -20, LAY, 20).kunciE(5, Easing.CUBIC_IN, RAY, -35, LAY, 35)
				.suara(5, "block.bamboo.hit", 0.1F, 1.8F)
				// children clap above the head and hop; women clap softly close under the chin
				.varian(SY_ANAK, v -> v.durasi(8).wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
						.kunci(0, RAX, -150, LAX, -150, RAY, -15, LAY, 15, LIFT, 0).kunci(2, LIFT, 0.05F)
						.kunciE(4, Easing.CUBIC_IN, RAY, -30, LAY, 30, LIFT, 0).suara(4, "block.bamboo.hit", 0.1F, 2.0F))
				.varian(SY_P, v -> v.durasi(12)
						.kunci(0, RAX, -95, LAX, -95, RAY, -22, LAY, 22, HX, 6, HZ, -4).kunciE(6, Easing.CUBIC_IN, RAY, -34, LAY, 34)
						.suara(6, "block.bamboo.hit", 0.06F, 2.0F))
				.bangun());
		d.accept(b(GerakId.TEPUK_BAHU, 30).sekali().bendera(ARAH_SASARAN).wajah(-1, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, 0, RAY, 0).kunciE(10, Easing.CUBIC_OUT, RAX, -100, RAY, -30).kunci(14, RAX, -92).kunci(16, RAX, -100)
				.kunci(18, RAX, -92).kunci(20, RAX, -100).kunci(26, RAX, -100, RAY, -30).kunci(30, RAX, 0, RAY, 0)
				// a child cannot reach the shoulder: it tugs the elder's sleeve, looking up
				.varian(SY_ANAK, v -> v
						.kunci(0, RAX, 0, RAY, 0, HX, 0).kunciE(8, Easing.CUBIC_OUT, RAX, -45, RAY, -20, HX, -15).kunci(12, RAX, -38)
						.kunci(16, RAX, -45).kunci(20, RAX, -38).kunci(26, RAX, -45, RAY, -20, HX, -15).kunci(30, RAX, 0, RAY, 0, HX, 0))
				.bangun());
		d.accept(b(GerakId.ANGGUK_DENGAR, 60).loop().lapisan(Lapisan.ATAS)
				.kunci(0, RAX, -30, RAY, -32, LAX, -30, LAY, 32, BZ, 1.5F, HX, 0).kunci(10, HX, 10).kunci(20, HX, 0).kunci(35, HX, 8)
				.kunci(45, HX, 0)
				// men listen with the hands clasped behind the back (108)
				.varian(SY_L, v -> v.kunci(0, RAX, 28.6F, RAZ, -12.6F, LAX, 28.6F, LAZ, 12.6F, HX, 0).kunci(10, HX, 9).kunci(20, HX, 0)
						.kunci(35, HX, 7).kunci(45, HX, 0))
				.bangun());
		d.accept(b(GerakId.LARI_LAMBAI, 12).loop().lapisan(Lapisan.ATAS).bendera(JALAN_OK).wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -160, RAZ, 20).osc(RAZ, 25, 12)
				.varian(SY_P, v -> v.kunci(0, RAX, -135, RAY, -15, RAZ, 12).osc(RAZ, 14, 12))
				.bangun());
		d.accept(b(GerakId.TIDUR_MEJA, 120).loop().bendera(DUDUK | KAKI_PAKSA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_TIDUR)
				.kunci(0, RLX, -90, LLX, -90, LIFT, -0.5F, LEAN, 40, HX, 40, RAX, -80, RAY, -40, LAX, -80, LAY, 40, NAPAS, 1.2F)
				.bangun());
		d.accept(b(GerakId.MARAH_HENTAK, 30).sekali().bendera(KAKI_PAKSA).wajah(Wajah.MATA_MARAH, Wajah.MULUT_MARAH)
				.kunci(0, RLX, -40, RAZ, 15, LAZ, -15, LIFT, 0).kunciE(8, Easing.CUBIC_IN, RLX, 0, LIFT, -0.05F).kunci(12, LIFT, 0)
				.kunci(24, RAZ, 15, LAZ, -15).kunci(30, RAZ, 0, LAZ, 0)
				.suara(8, "entity.player.big_fall", 0.2F, 1.1F).partikel(8, "debu", 3, 0).bangun());
		d.accept(b(GerakId.BICARA, 74).loop().lapisan(Lapisan.KEPALA).bendera(BICARA)
				.kunci(0, HX, 0, HY, 0).osc(HX, 3, 14).osc(HY, 4, 37).bangun());
	}

	// ================================================================== 40.5.13 autonomous disciples 172-176

	private static void mandiri(Consumer<Klip> d) {
		d.accept(b(GerakId.LATIH_JURUS, 60).loop().wajah(Wajah.MATA_MARAH, -1)
				.maskSumber(HX, HY, LEAN, TWIST, RAX, RAY, RAZ, LAX, LAY, LAZ, RLX, RLZ, LLX, LLZ, RLY, LLY, LIFT)
				.duel(DuelPose.TUSUK, 15).duel(DuelPose.PUTAR, 15).duel(DuelPose.TANGKIS, 15).duel(DuelPose.KUDA_TENGAH, 15)
				.osc(TWIST, 12, 60).kait(15, "mandiri:busur").kait(30, "mandiri:busur")
				.suara(10, "entity.player.attack.sweep", 0.2F, 1.3F).suara(25, "entity.player.attack.sweep", 0.2F, 1.1F)
				.prop(PropId.PEDANG_KAYU, 0, 0).bangun());
		d.accept(b(GerakId.BERKEMAS, 30).loop().bendera(KAKI_PAKSA | LIRIK_BAWAH)
				.kunci(0, RLX, -90, LLX, 70, LIFT, -0.4F, LEAN, 15, HX, 30, RAX, -60, LAX, -50).kunci(15, RAX, -30)
				.suara(15, "item.bundle.insert", 0.15F, 1.0F).bangun());
		d.accept(b(GerakId.LIHAT_BARANG, 40).loop().bendera(LIRIK_BAWAH)
				.kunci(0, LEAN, 20, HX, 25, RAX, -70, LAX, -70, RAY, -20, LAY, 20).osc(RAY, 20, 40).osc(LAY, -20, 40).bangun());
		d.accept(b(GerakId.BERSANDAR, 120).loop().bendera(DUDUK | KAKI_PAKSA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_TIDUR)
				.kunci(0, LIFT, -0.55F, RLX, -80, LLX, -60, RLY, 10, LEAN, -15, HX, 10, RAX, -20, LAX, -10, LAZ, -10)
				.kait(0, "mandiri:z").kait(60, "mandiri:z").bangun());
		// LangitPoses.fly numbers (patrol of 37): sway period 69.8, gust period 27.3
		d.accept(b(GerakId.BERDIRI_DI_PEDANG, 140).loop().bendera(KAKI_PAKSA)
				.kunci(0, RLX, 2.9F, RLZ, 3.4F, LLX, -2.9F, LLZ, -3.4F, LEAN, 10.3F, BZ, 0, RAX, -77.3F, RAY, -31.5F, RAZ, 6.9F,
						LAX, 31.5F, LAY, 14.3F, LAZ, 20, HX, 5.7F, HZ, 0)
				.osc(LEAN, 2.3F, 27.3F, 1.1F, Osilator.Bentuk.SIN).osc(RAX, 2.3F, 27.3F, 1.1F, Osilator.Bentuk.SIN)
				.osc(BZ, 2.9F, 69.8F).osc(RAZ, 1.7F, 69.8F).osc(HX, 1.7F, 69.8F).osc(HZ, -2.3F, 69.8F).bangun());
	}

	private KatalogSosok() {
	}
}

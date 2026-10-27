package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.client.screen.SettingsRows;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.acara.KalenderKota;
import dev.kultivator.dunia.kota42.acara.MusimKota;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Firecrackers (DESIGN 42.12.6 {@code Petasan}): a string of 20-60 small bangs, nothing but particles and sound (no
 * {@code FireworkRocketEntity}, no damage, no block). A string hangs from the eave above a door or a banner pole, lit at the
 * bottom: the crackers go off one after another and faster and faster, each a flash, a few orange sparks, three scraps of red
 * paper, a puff of grey smoke, and the string shortens as it burns; the sound is the engine's small firework blast now and then
 * plus the bundled {@code kota42.petasan} strings (6 variants). What lights them (all client-side, from the atmosphere payload):
 * <ul>
 * <li>a wedding house (the special houses of {@code kota_suasana}): when the player comes within 48 blocks of its door, and again
 * every four minutes while the wedding lasts;</li>
 * <li>New Year: from the evening of the first day and at dawn, a string somewhere near the player every 25-60 seconds;</li>
 * <li>a festival evening: one every 90-150 seconds;</li>
 * <li>a ceremony of weddings ({@code kota_upacara}): a string where the sedan sets out and another where it arrives;</li>
 * <li>a plot that reaches its last stage ({@code kota_kavling}): the opening string over the new house.</li>
 * </ul>
 * Counts per string 0 / 30 / 60 (Minimal shows none), at most 3 strings alive; switch {@code kota42.petasan}. Client thread only.
 */
final class Petasan {

	private static final Identifier TEKS_UNTAI = Kultivator.id("textures/fx/kota42/untai_petasan.png");
	private static final int MAKS_AKTIF = 3;

	private static final class Untai {
		final double x;
		final double y;
		final double z;
		final int hadap;
		final long mulai;
		final int jumlah;
		final int durasi;
		int sudah;
		int suaraSampai;

		Untai(double x, double y, double z, int hadap, long mulai, int jumlah) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.hadap = hadap;
			this.mulai = mulai;
			this.jumlah = jumlah;
			this.durasi = Math.max(50, jumlah * 4);
		}

		/** Fraction 0-1 of the crackers gone at tick {@code umur}: slow first, then a rattle, then a few stragglers. */
		float kemajuan(float umur) {
			float t = Mth.clamp(umur / durasi, 0.0F, 1.0F);
			return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - 0.25F * t);
		}
	}

	private static final List<Untai> AKTIF = new ArrayList<>();
	private static final Map<Long, Long> TERAKHIR = new HashMap<>();
	private static final Map<Long, Integer> TAHAP_KAVLING = new HashMap<>();
	private static final Map<Integer, Integer> FASE_UPACARA = new HashMap<>();
	private static long berikut;

	static boolean ada() {
		return !AKTIF.isEmpty();
	}

	static void bersihkan() {
		AKTIF.clear();
		TERAKHIR.clear();
		TAHAP_KAVLING.clear();
		FASE_UPACARA.clear();
		berikut = 0L;
	}

	/** Hangs and lights a string of {@code jumlah} crackers whose eave point is {@code (x, y, z)} facing {@code hadap}. */
	static void picu(double x, double y, double z, int hadap, int jumlah) {
		if (!SettingsRows.kota42("kota42.petasan") || Bingkai.tier == 0 || AKTIF.size() >= MAKS_AKTIF || Bingkai.level == null) {
			return;
		}
		int maks = Bingkai.hitung(0, 30, 60);
		AKTIF.add(new Untai(x, y, z, hadap, Bingkai.waktu, Mth.clamp(jumlah, 8, maks)));
	}

	// ================================================================== triggers (twice a second)

	static void picuan() {
		var level = Bingkai.level;
		var pemain = Bingkai.pemain;
		if (level == null || pemain == null || Bingkai.tier == 0 || !SettingsRows.kota42("kota42.petasan")) {
			return;
		}
		RandomSource rnd = level.getRandom();
		Vec3 p = pemain.position();
		for (KotaPayloads.Suasana s : KotaHidupClient.semuaSuasana()) {
			TitikPeta.Pusat pc = TitikPeta.pusat(s.situs());
			if (pc == null) {
				continue;
			}
			double dx = pc.x() - p.x;
			double dz = pc.z() - p.z;
			double luar = pc.radius() + 90.0D;
			if (dx * dx + dz * dz > luar * luar) {
				continue;
			}
			// weddings: the bride's door
			for (KotaPayloads.EntriRumah r : s.rumah()) {
				if (r.jenis() != KotaPayloads.RUMAH_NIKAH) {
					continue;
				}
				for (TitikPeta.Kain k : TitikPeta.kain) {
					if (k.situs() != s.situs() || k.kain() != TitikKode.KAIN_PINTU || k.rumah() != r.rumah()) {
						continue;
					}
					double d2 = (k.x() - p.x) * (k.x() - p.x) + (k.z() - p.z) * (k.z() - p.z);
					long key = s.situs() * 31L + r.rumah();
					Long last = TERAKHIR.get(key);
					if (d2 < 48.0D * 48.0D && (last == null || Bingkai.waktu - last > 4800L)) {
						TERAKHIR.put(key, Bingkai.waktu);
						picu(k.x() + TitikKode.dx(k.hadap()) * 0.9D, k.y() + 2.75D, k.z() + TitikKode.dz(k.hadap()) * 0.9D, k.hadap(), 40 + rnd.nextInt(21));
					}
				}
			}
			boolean tahunBaru = (s.acara() & KalenderKota.BIT_TAHUN_BARU) != 0 || s.hariRaya() == MusimKota.HR_TAHUN_BARU;
			boolean raya = s.hariRaya() != MusimKota.HR_TIDAK && s.hariRaya() != MusimKota.HR_QINGMING;
			boolean waktuMeriah = Bingkai.antara(18.0D, 24.0D) || Bingkai.antara(5.0D, 8.0D);
			if ((tahunBaru && waktuMeriah || raya && Bingkai.antara(18.0D, 23.0D)) && Bingkai.waktu >= berikut) {
				berikut = Bingkai.waktu + (tahunBaru ? 500L + rnd.nextInt(700) : 1800L + rnd.nextInt(1200));
				TitikPeta.Kain dipilih = null;
				int pilih = 0;
				for (TitikPeta.Kain k : TitikPeta.kain) {
					if (k.situs() != s.situs() || k.kain() != TitikKode.KAIN_PINTU && k.kain() != TitikKode.KAIN_PANJI) {
						continue;
					}
					double d2 = (k.x() - p.x) * (k.x() - p.x) + (k.z() - p.z) * (k.z() - p.z);
					if (d2 < 10.0D * 10.0D || d2 > 60.0D * 60.0D) {
						continue;
					}
					// reservoir sampling over the candidates in the ring: every one has the same chance
					pilih++;
					if (rnd.nextInt(pilih) == 0) {
						dipilih = k;
					}
				}
				if (dipilih != null) {
					picu(dipilih.x() + TitikKode.dx(dipilih.hadap()) * 0.9D, dipilih.y() + 2.75D, dipilih.z() + TitikKode.dz(dipilih.hadap()) * 0.9D, dipilih.hadap(),
							tahunBaru ? 30 + rnd.nextInt(31) : 20 + rnd.nextInt(11));
				}
			}
		}
		// the wedding procession: a string where it sets out and where it arrives
		for (KotaPayloads.Upacara u : KotaHidupClient.upacara()) {
			if (u.jenis() != JenisUpacara.NIKAH.ordinal() || u.simpul().length == 0) {
				continue;
			}
			Integer lama = FASE_UPACARA.get(u.id());
			if (lama == null || lama != u.fase()) {
				FASE_UPACARA.put(u.id(), u.fase());
				BlockPos node = BlockPos.of(u.simpul()[u.fase() <= 0 || u.fase() % 2 == 1 ? 0 : u.simpul().length - 1]);
				if (node.distSqr(pemain.blockPosition()) < 70.0D * 70.0D) {
					picu(node.getX() + 0.5D, node.getY() + 3.0D, node.getZ() + 0.5D, 0, 36);
				}
			}
		}
		// the opening of a plot
		for (KotaPayloads.Kavling kv : KotaHidupClient.kavling()) {
			long key = kv.situs() * 31L + kv.kavling();
			Integer lama = TAHAP_KAVLING.put(key, kv.tahap());
			if (kv.tahap() >= 4 && lama != null && lama < 4) {
				BlockPos o = BlockPos.of(kv.asal());
				if (o.distSqr(pemain.blockPosition()) < 80.0D * 80.0D) {
					int hx = kv.kotak().length >= 6 ? (kv.kotak()[0] + kv.kotak()[3]) / 2 : 0;
					int hy = kv.kotak().length >= 6 ? kv.kotak()[4] : 4;
					int hz = kv.kotak().length >= 6 ? (kv.kotak()[2] + kv.kotak()[5]) / 2 : 0;
					picu(o.getX() + hx + 0.5D, o.getY() + hy + 2.0D, o.getZ() + hz + 0.5D, 0, 40);
				}
			}
		}
	}

	// ================================================================== tick

	static void tik() {
		var level = Bingkai.level;
		if (level == null || AKTIF.isEmpty()) {
			return;
		}
		RandomSource rnd = level.getRandom();
		for (Iterator<Untai> it = AKTIF.iterator(); it.hasNext(); ) {
			Untai u = it.next();
			float umur = Bingkai.waktu - u.mulai;
			int seharusnya = Math.round(u.jumlah * u.kemajuan(umur));
			if (umur >= u.suaraSampai) {
				float v = 0.55F + rnd.nextFloat() * 0.2F;
				SuaraKota42.suara(level, u.x, u.y - 1.2D, u.z, SuaraKota42.acara("kota42.petasan"), v, 0.95F + rnd.nextFloat() * 0.1F);
				u.suaraSampai = (int) umur + 22 + rnd.nextInt(10);
			}
			while (u.sudah < seharusnya) {
				u.sudah++;
				float f = u.sudah / (float) u.jumlah;
				double by = u.y - PANJANG * (1.0F - f) - 0.1D;
				letus(level, rnd, u, by);
			}
			if (umur > u.durasi + 24) {
				it.remove();
			}
		}
	}

	private static final float PANJANG = 1.9F;

	/** One cracker at height {@code by} of the string. */
	private static void letus(net.minecraft.client.multiplayer.ClientLevel level, RandomSource rnd, Untai u, double by) {
		float x = (float) (u.x + (rnd.nextFloat() - 0.5F) * 0.15F);
		float z = (float) (u.z + (rnd.nextFloat() - 0.5F) * 0.15F);
		float y = (float) by;
		PartikelKota.kilau(rnd, x, y, z, 0.28F + rnd.nextFloat() * 0.1F, 3, 0xFFD890);
		int sparks = 2 + rnd.nextInt(3);
		for (int i = 0; i < sparks; i++) {
			PartikelKota.percik(rnd, x, y, z, (rnd.nextFloat() - 0.5F) * 0.16F, 0.02F + rnd.nextFloat() * 0.09F, (rnd.nextFloat() - 0.5F) * 0.16F, 9 + rnd.nextInt(8), 0.07F);
		}
		for (int i = 0; i < 3; i++) {
			PartikelKota.kertasMerah(rnd, x, y, z, (rnd.nextFloat() - 0.5F) * 0.14F, 0.03F + rnd.nextFloat() * 0.06F, (rnd.nextFloat() - 0.5F) * 0.14F);
		}
		if (rnd.nextInt(3) == 0) {
			PartikelKota.asap(rnd, x, y, z, 0.5F, 0xB8B6B2);
		}
		if (rnd.nextInt(4) == 0) {
			SuaraKota42.suara(level, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, 0.16F, 1.5F + rnd.nextFloat() * 0.5F);
		}
	}

	// ================================================================== frame

	static void gambar() {
		if (AKTIF.isEmpty()) {
			return;
		}
		var cam = Bingkai.kamera;
		Geo.Sink s = Geo.tipe(TEKS_UNTAI, Geo.Jenis.POTONG);
		for (Untai u : AKTIF) {
			float umur = Bingkai.waktu + Bingkai.sebagian - u.mulai;
			float terbakar = u.kemajuan(umur);
			float sisa = PANJANG * (1.0F - terbakar);
			if (sisa < 0.12F) {
				continue;
			}
			float cx = (float) (u.x - cam.x);
			float cy = (float) (u.y - cam.y);
			float cz = (float) (u.z - cam.z);
			float lebar = 0.34F;
			float v1 = Mth.clamp(sisa / PANJANG, 0.05F, 1.0F);
			int light = Bingkai.cahaya(u.x, u.y - 1.0D, u.z);
			// the string hangs from the eave: top fixed, the bottom end shows the part still to burn; two crossed planes
			float[] uv = {0.0F, 0.0F, 1.0F, v1};
			float sway = Mth.sin(Bingkai.waktuF * 0.21F + (float) u.x) * 0.04F;
			s.quad(cx - lebar * 0.5F + sway, cy, cz, cx - lebar * 0.5F + sway * 2.0F, cy - sisa, cz, cx + lebar * 0.5F + sway * 2.0F, cy - sisa, cz, cx + lebar * 0.5F + sway, cy, cz,
					uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light, true);
			s.quad(cx, cy, cz - lebar * 0.5F + sway, cx, cy - sisa, cz - lebar * 0.5F + sway * 2.0F, cx, cy - sisa, cz + lebar * 0.5F + sway * 2.0F, cx, cy, cz + lebar * 0.5F + sway,
					uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light, true);
		}
	}

	private Petasan() {
	}
}

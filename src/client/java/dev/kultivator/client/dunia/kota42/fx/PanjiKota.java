package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.acara.KalenderKota;
import dev.kultivator.dunia.kota42.acara.MusimKota;
import dev.kultivator.sekte.api.Lambang;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banners, boards, street bunting and door papers that change with what happens in the town (DESIGN 42.9.3). Each titik of
 * {@code panji} kind carries the kind of cloth ({@link TitikKode}) and the atmosphere of its place ({@code kota_suasana}) decides
 * what it shows:
 * <ul>
 * <li><b>panji</b> (gate, tower, street pole; or flat on a wall when a solid block is behind it): the district's own colour
 * normally (the plain banner tinted by a hash of the place, with the titik's glyphs in cream), red-gold {@code festival} /
 * {@code festival_shou} on a festival, {@code tahun_baru} (春) at New Year, golden {@code panen} at the harvest, black-red
 * {@code perang} (戒) under attack, and the protector's emblem on a banner in the colour of the protector's border when a player
 * sect protects the place;</li>
 * <li><b>bendera_arak</b>: the swallow-tailed wine flag (酒) of the taverns; <b>papan</b>: a lacquered board with a gold glyph;</li>
 * <li><b>spanduk</b>: the pair of poles across the main street carries a rope of pennants (festival colours or the district's);</li>
 * <li><b>kertas_pintu</b>: nothing on an ordinary day; New Year pastes red couplets on the door frame and an upside-down 福 on the
 * door; the house of a wedding (the atmosphere's special houses) shows a red diamond with 囍; a house of mourning hangs white
 * hemp with 奠.</li>
 * </ul>
 * Cloth is {@link KainKota}: sine waves growing toward the free end, leaning with the day's wind. Budget 12 / 36 / 96 cloth units
 * (a pennant is one) within 72 blocks; nothing in a shader shadow pass (the caller). Client thread only.
 */
final class PanjiKota {

	private static final String PANJI = "textures/kota42/panji/";
	private static final String GLIF = "textures/kota42/glif/";
	private static final Identifier LAMBANG = Kultivator.id("textures/sekte/seragam/lambang_atlas.png");
	private static final Identifier KUPLET_KIRI = Kultivator.id("textures/kota42/kuplet_kiri.png");
	private static final Identifier KUPLET_KANAN = Kultivator.id("textures/kota42/kuplet_kanan.png");
	private static final Identifier PAPAN = Kultivator.id("textures/fx/kota42/papan.png");
	private static final Identifier KERTAS_PINTU = Kultivator.id("textures/fx/kota42/kertas_pintu.png");
	private static final Identifier DUKA = Kultivator.id("textures/fx/kota42/duka.png");
	private static final Identifier TALI = Kultivator.id("textures/fx/kota42/tali.png");
	private static final float[] PENUH = {0.0F, 0.0F, 1.0F, 1.0F};
	private static final int[] DISTRIK = {0xC2584A, 0x4F86A2, 0x4C9470, 0x9A6AAE, 0xCC9A4A, 0x5C6CB4, 0xB4546A, 0x78A25C};
	private static final int KRIM = 0xF8E8B0;

	private static List<TitikPeta.Kain> kainDekat = List.of();
	private static List<TitikPeta.Pasangan> spandukDekat = List.of();
	private static final Map<Long, Boolean> MENEMPEL = new HashMap<>();
	private static int detak;

	static void tik() {
		var pemain = Bingkai.pemain;
		if (pemain == null) {
			return;
		}
		if (detak++ % 8 != 0) {
			return;
		}
		int maks = Bingkai.hitung(12, 36, 96);
		double r2 = 72.0D * 72.0D;
		double px = pemain.getX();
		double pz = pemain.getZ();
		List<TitikPeta.Kain> k = new ArrayList<>();
		for (TitikPeta.Kain c : TitikPeta.kain) {
			double dx = c.x() - px;
			double dz = c.z() - pz;
			if (dx * dx + dz * dz <= r2) {
				k.add(c);
			}
		}
		k.sort(Comparator.comparingDouble(c -> (c.x() - px) * (c.x() - px) + (c.z() - pz) * (c.z() - pz)));
		kainDekat = k.size() > maks ? new ArrayList<>(k.subList(0, maks)) : k;
		List<TitikPeta.Pasangan> p = new ArrayList<>();
		for (TitikPeta.Pasangan c : TitikPeta.spanduk) {
			double dx = (c.a().x() + c.b().x()) * 0.5D - px;
			double dz = (c.a().z() + c.b().z()) * 0.5D - pz;
			if (dx * dx + dz * dz <= r2) {
				p.add(c);
			}
		}
		p.sort(Comparator.comparingDouble(c -> sq((c.a().x() + c.b().x()) * 0.5D - px) + sq((c.a().z() + c.b().z()) * 0.5D - pz)));
		spandukDekat = p;
	}

	private static double sq(double v) {
		return v * v;
	}

	static void bersihkan() {
		kainDekat = List.of();
		spandukDekat = List.of();
		MENEMPEL.clear();
	}

	// ================================================================== frame

	static void gambar() {
		if (kainDekat.isEmpty() && spandukDekat.isEmpty()) {
			return;
		}
		int sisa = Bingkai.hitung(12, 36, 96);
		for (TitikPeta.Kain k : kainDekat) {
			if (sisa <= 0) {
				break;
			}
			KotaPayloads.Suasana s = KotaHidupClient.suasana(k.situs());
			sisa -= switch (k.kain()) {
				case TitikKode.KAIN_PANJI -> panji(k, s);
				case TitikKode.KAIN_ARAK -> arak(k);
				case TitikKode.KAIN_PAPAN -> papan(k);
				case TitikKode.KAIN_PINTU -> pintu(k, s);
				default -> 0;
			};
		}
		for (TitikPeta.Pasangan p : spandukDekat) {
			if (sisa <= 0) {
				break;
			}
			sisa -= spanduk(p, KotaHidupClient.suasana(p.a().situs()), sisa);
		}
	}

	// ================================================================== the cloth kinds

	/** What a banner shows: the texture name under {@link #PANJI} and its tint (-1 = none), {@code sekte} the emblem. */
	private record Isi(String tekstur, int tint, @Nullable Lambang lambang) {
	}

	private static Isi isi(long situs, int hash, KotaPayloads.@Nullable Suasana s) {
		int bits = s == null ? 0 : s.acara();
		int raya = s == null ? 0 : s.hariRaya();
		if ((bits & KalenderKota.BIT_SERANGAN) != 0) {
			return new Isi("perang", -1, null);
		}
		if ((bits & KalenderKota.BIT_TAHUN_BARU) != 0 || raya == MusimKota.HR_TAHUN_BARU) {
			return new Isi("tahun_baru", -1, null);
		}
		if (raya == MusimKota.HR_PANEN) {
			return new Isi("panen", -1, null);
		}
		if (raya != MusimKota.HR_TIDAK && raya != MusimKota.HR_QINGMING) {
			return new Isi((hash & 1) == 0 ? "festival" : "festival_shou", -1, null);
		}
		if (s != null && s.lambang() >= 0) {
			Lambang l = Lambang.unpack(s.lambang());
			return new Isi("sekte", l.borderRgb() & 0xFFFFFF, l);
		}
		int warna = raya == MusimKota.HR_QINGMING ? 0xB8C4B4 : DISTRIK[(int) Math.floorMod(situs ^ situs >>> 17, (long) DISTRIK.length)];
		return new Isi("biasa", warna, null);
	}

	/** True when a solid block is right behind the titik (a banner flat on a wall rather than on a pole). */
	private static boolean menempel(TitikPeta.Kain k) {
		var level = Bingkai.level;
		if (level == null) {
			return false;
		}
		long key = BlockPos.asLong(Math.round(k.x() - 0.5F), (int) k.y(), Math.round(k.z() - 0.5F)) * 4 + k.hadap();
		Boolean b = MENEMPEL.get(key);
		if (b != null) {
			return b;
		}
		BlockPos belakang = new BlockPos(Math.round(k.x() - 0.5F) - TitikKode.dx(k.hadap()), (int) k.y(), Math.round(k.z() - 0.5F) - TitikKode.dz(k.hadap()));
		if (!level.hasChunkAt(belakang)) {
			return false;
		}
		boolean solid = !level.getBlockState(belakang).getCollisionShape(level, belakang).isEmpty();
		MENEMPEL.put(key, solid);
		return solid;
	}

	private static int panji(TitikPeta.Kain k, KotaPayloads.@Nullable Suasana s) {
		Isi isi = isi(k.situs(), k.hash(), s);
		boolean dinding = menempel(k);
		float nx = TitikKode.dx(k.hadap());
		float nz = TitikKode.dz(k.hadap());
		var cam = Bingkai.kamera;
		float lebar = dinding ? 0.86F : 1.0F;
		float tinggi = dinding ? 1.72F : 2.0F;
		float geser = dinding ? -0.45F : 0.58F;
		float cx = (float) (k.x() + nx * geser - cam.x);
		float cy = (float) (k.y() + (dinding ? 0.95F : 0.85F) - cam.y);
		float cz = (float) (k.z() + nz * geser - cam.z);
		int light = Bingkai.cahaya(k.x() + nx * 0.7D, k.y() + 0.5D, k.z() + nz * 0.7D);
		float amp = dinding ? 0.025F : 0.16F;
		float angin = dinding ? 0.0F : 0.12F * (PartikelKota.anginX() * nx + PartikelKota.anginZ() * nz) / 0.02F;
		int tint = isi.tint() < 0 ? 0xFFFFFFFF : 0xFF000000 | isi.tint();
		Identifier tex = Kultivator.id(PANJI + isi.tekstur() + ".png");
		float fase = (k.hash() & 1023) / 163.0F;
		Geo.Sink s1 = Geo.tipe(tex, Geo.Jenis.POTONG);
		KainKota.kain(s1, cx, cy, cz, nx, nz, lebar, tinggi, 8, Bingkai.waktuF, fase, amp, angin, PENUH, tint, light);
		if (!dinding) {
			palang(cx, cy, cz, nx, nz, lebar + 0.12F, light);
		}
		// the glyphs of the titik on a plain banner (the festival banners carry their own)
		if (isi.tekstur().equals("biasa") && !k.glif().isEmpty()) {
			for (int i = 0; i < k.glif().size(); i++) {
				float gy = cy - tinggi * (0.3F + 0.3F * i) * (k.glif().size() > 1 ? 1.0F : 1.2F);
				glif(k.glif().get(i), cx + nx * 0.02F, gy, cz + nz * 0.02F, nx, nz, 0.6F, false, 0xFF000000 | KRIM, light);
				glif(k.glif().get(i), cx - nx * 0.02F, gy, cz - nz * 0.02F, -nx, -nz, 0.6F, false, 0xFF000000 | KRIM, light);
			}
		}
		if (isi.lambang() != null) {
			lambang(isi.lambang(), cx, cy - tinggi * 0.42F, cz, nx, nz, 0.7F, light);
			lambang(isi.lambang(), cx, cy - tinggi * 0.42F, cz, -nx, -nz, 0.7F, light);
		}
		return 1;
	}

	private static int arak(TitikPeta.Kain k) {
		float nx = TitikKode.dx(k.hadap());
		float nz = TitikKode.dz(k.hadap());
		var cam = Bingkai.kamera;
		boolean dinding = menempel(k);
		float geser = dinding ? -0.45F : 0.5F;
		float cx = (float) (k.x() + nx * geser - cam.x);
		float cy = (float) (k.y() + 0.85F - cam.y);
		float cz = (float) (k.z() + nz * geser - cam.z);
		int light = Bingkai.cahaya(k.x() + nx * 0.7D, k.y() + 0.5D, k.z() + nz * 0.7D);
		float angin = dinding ? 0.0F : 0.1F * (PartikelKota.anginX() * nx + PartikelKota.anginZ() * nz) / 0.02F;
		KainKota.kain(Geo.tipe(Kultivator.id(PANJI + "arak.png"), Geo.Jenis.POTONG), cx, cy, cz, nx, nz, 0.78F, 1.56F, 8, Bingkai.waktuF, (k.hash() & 1023) / 163.0F,
				dinding ? 0.02F : 0.14F, angin, PENUH, 0xFFFFFFFF, light);
		if (!dinding) {
			palang(cx, cy, cz, nx, nz, 0.9F, light);
		}
		return 1;
	}

	private static int papan(TitikPeta.Kain k) {
		float nx = TitikKode.dx(k.hadap());
		float nz = TitikKode.dz(k.hadap());
		var cam = Bingkai.kamera;
		boolean dinding = menempel(k);
		float geser = dinding ? -0.455F : 0.0F;
		float cx = (float) (k.x() + nx * geser - cam.x);
		float cy = (float) (k.y() + 0.5F - cam.y);
		float cz = (float) (k.z() + nz * geser - cam.z);
		int light = Bingkai.cahaya(k.x() + nx * 0.6D, k.y() + 0.5D, k.z() + nz * 0.6D);
		KainKota.kotak(Geo.tipe(PAPAN, Geo.Jenis.POTONG), cx, cy, cz, nx, nz, 0.86F, 0.86F, false, PENUH, 0xFFFFFFFF, light, true);
		for (int i = 0; i < k.glif().size(); i++) {
			float gy = cy + (k.glif().size() > 1 ? (i == 0 ? 0.2F : -0.2F) : 0.0F);
			float sz = k.glif().size() > 1 ? 0.36F : 0.56F;
			glif(k.glif().get(i), cx + nx * 0.012F, gy, cz + nz * 0.012F, nx, nz, sz, false, 0xFFE6BE5A, light);
			glif(k.glif().get(i), cx - nx * 0.012F, gy, cz - nz * 0.012F, -nx, -nz, sz, false, 0xFFE6BE5A, light);
		}
		return 1;
	}

	private static int pintu(TitikPeta.Kain k, KotaPayloads.@Nullable Suasana s) {
		if (s == null) {
			return 0;
		}
		int jenis = -1;
		if (k.rumah() != TitikKode.TIDAK_ADA) {
			for (KotaPayloads.EntriRumah r : s.rumah()) {
				if (r.rumah() == k.rumah()) {
					jenis = r.jenis();
					break;
				}
			}
		}
		boolean tahunBaru = (s.acara() & KalenderKota.BIT_TAHUN_BARU) != 0 || s.hariRaya() == MusimKota.HR_TAHUN_BARU;
		if (jenis < 0 && !tahunBaru) {
			return 0;
		}
		float nx = TitikKode.dx(k.hadap());
		float nz = TitikKode.dz(k.hadap());
		var cam = Bingkai.kamera;
		// the titik is the air cell in front of the door; the door plane is half a block behind its middle
		float cx = (float) (k.x() - nx * 0.47F - cam.x);
		float cz = (float) (k.z() - nz * 0.47F - cam.z);
		float y0 = (float) (k.y() - cam.y);
		int light = Bingkai.cahaya(k.x() + nx * 0.2D, k.y() + 1.0D, k.z() + nz * 0.2D);
		int n = 1;
		if (jenis == KotaPayloads.RUMAH_NIKAH) {
			Geo.Sink papan = Geo.tipe(KERTAS_PINTU, Geo.Jenis.POTONG);
			KainKota.kotak(papan, cx, y0 + 1.25F, cz, nx, nz, 0.85F, 0.85F, false, PENUH, 0xFFFFFFFF, light, true);
			glif("xi", cx + nx * 0.012F, y0 + 1.25F, cz + nz * 0.012F, nx, nz, 0.5F, false, 0xFFF6D060, light);
			glif("xi", cx - nx * 0.012F, y0 + 1.25F, cz - nz * 0.012F, -nx, -nz, 0.5F, false, 0xFFF6D060, light);
			// a red ribbon of cloth on each side of the door frame
			float rx = nz * 0.62F;
			float rz = -nx * 0.62F;
			Geo.Sink pita = Geo.tipe(Kultivator.id(PANJI + "festival.png"), Geo.Jenis.POTONG);
			float[] atas = {0.0F, 0.0F, 1.0F, 0.55F};
			for (int sisi = -1; sisi <= 1; sisi += 2) {
				KainKota.kain(pita, cx + rx * sisi + nx * 0.03F, y0 + 2.05F, cz + rz * sisi + nz * 0.03F, nx, nz, 0.3F, 1.1F, 4, Bingkai.waktuF, k.hash() * 0.01F + sisi, 0.05F, 0.0F,
						atas, 0xFFFFFFFF, light);
			}
			n = 3;
		} else if (jenis == KotaPayloads.RUMAH_DUKA) {
			KainKota.kain(Geo.tipe(DUKA, Geo.Jenis.POTONG), cx + nx * 0.03F, y0 + 2.25F, cz + nz * 0.03F, nx, nz, 0.9F, 1.35F, 6, Bingkai.waktuF, k.hash() * 0.013F, 0.045F, 0.0F,
					PENUH, 0xFFFFFFFF, light);
			glif("dian", cx + nx * 0.05F, y0 + 1.62F, cz + nz * 0.05F, nx, nz, 0.5F, false, 0xFF202020, light);
			glif("dian", cx - nx * 0.05F, y0 + 1.62F, cz - nz * 0.05F, -nx, -nz, 0.5F, false, 0xFF202020, light);
			n = 2;
		} else if (tahunBaru) {
			// red couplets on the door frame and the upside-down fu on the door leaf
			float rx = nz * 0.6F;
			float rz = -nx * 0.6F;
			KainKota.kotak(Geo.tipe(KUPLET_KIRI, Geo.Jenis.POTONG), cx - rx + nx * 0.02F, y0 + 1.1F, cz - rz + nz * 0.02F, nx, nz, 0.32F, 1.95F, false, PENUH, 0xFFFFFFFF, light, true);
			KainKota.kotak(Geo.tipe(KUPLET_KANAN, Geo.Jenis.POTONG), cx + rx + nx * 0.02F, y0 + 1.1F, cz + rz + nz * 0.02F, nx, nz, 0.32F, 1.95F, false, PENUH, 0xFFFFFFFF, light, true);
			KainKota.kotak(Geo.tipe(KERTAS_PINTU, Geo.Jenis.POTONG), cx + nx * 0.015F, y0 + 1.45F, cz + nz * 0.015F, nx, nz, 0.62F, 0.62F, false, PENUH, 0xFFFFFFFF, light, true);
			glif("fu", cx + nx * 0.03F, y0 + 1.45F, cz + nz * 0.03F, nx, nz, 0.4F, true, 0xFFF6D060, light);
			glif("fu", cx - nx * 0.03F, y0 + 1.45F, cz - nz * 0.03F, -nx, -nz, 0.4F, true, 0xFFF6D060, light);
			n = 2;
		}
		return n;
	}

	/** The pennant rope across the street (pairs of spanduk titik). Returns the cloth units used (at most {@code sisa}). */
	private static int spanduk(TitikPeta.Pasangan p, KotaPayloads.@Nullable Suasana s, int sisa) {
		TitikPeta.Spanduk a = p.a();
		TitikPeta.Spanduk b = p.b();
		var cam = Bingkai.kamera;
		float ax = (float) (a.x() - cam.x);
		float ay = (float) (a.y() + 0.5 - cam.y);
		float az = (float) (a.z() - cam.z);
		float bx = (float) (b.x() - cam.x);
		float by = (float) (b.y() + 0.5 - cam.y);
		float bz = (float) (b.z() - cam.z);
		float dx = bx - ax;
		float dz = bz - az;
		float len = Mth.sqrt(dx * dx + dz * dz);
		if (len < 2.0F) {
			return 0;
		}
		float lendut = Math.min(1.1F, len * 0.07F);
		int light = Bingkai.cahaya((a.x() + b.x()) * 0.5D, (a.y() + b.y()) * 0.5D, (a.z() + b.z()) * 0.5D);
		KainKota.tali(Geo.tipe(TALI, Geo.Jenis.POTONG), ax, ay, az, bx, by, bz, lendut, 0.025F, 0xFFFFFFFF, light);
		Isi isi = isi(a.situs(), a.hash(), s);
		// the pennants are the banner colours of the place; a plain day hangs the district colour pale
		int jumlah = Math.min(Math.max(1, Math.round(len / 2.3F)), sisa);
		float nx = dz / len;
		float nz = -dx / len;
		float waktu = Bingkai.waktuF;
		String[] bergilir = isi.tekstur().equals("festival") || isi.tekstur().equals("festival_shou") ? new String[] {"festival", "festival_shou"} : new String[] {isi.tekstur()};
		for (int i = 0; i < jumlah; i++) {
			float f = (i + 0.5F) / jumlah;
			float x = ax + dx * f;
			float z = az + dz * f;
			float y = KainKota.taliY(ay, by, lendut, f) - 0.02F;
			String nama = bergilir[i % bergilir.length];
			int tint = isi.tint() < 0 ? 0xFFFFFFFF : 0xFF000000 | isi.tint();
			Geo.Sink sink = Geo.tipe(Kultivator.id(PANJI + nama + ".png"), Geo.Jenis.POTONG);
			float angin = 0.08F * (PartikelKota.anginX() * nx + PartikelKota.anginZ() * nz) / 0.02F;
			float fase = (a.hash() & 255) / 40.0F + i * 1.9F;
			KainKota.kain(sink, x, y, z, nx, nz, 0.72F, 1.3F, 6, waktu, fase, 0.12F, angin, PENUH, tint, light);
			if (isi.lambang() != null) {
				lambang(isi.lambang(), x, y - 0.52F, z, nx, nz, 0.5F, light);
				lambang(isi.lambang(), x, y - 0.52F, z, -nx, -nz, 0.5F, light);
			}
		}
		return jumlah;
	}

	// ================================================================== parts

	/** The crossbar of a hung banner. */
	private static void palang(float cx, float cy, float cz, float nx, float nz, float lebar, int light) {
		Geo.Sink s = Geo.tipe(TALI, Geo.Jenis.POTONG);
		KainKota.kotak(s, cx, cy + 0.035F, cz, nx, nz, lebar, 0.07F, false, PENUH, 0xFF6A4A2A, light, true);
		// the top face of the bar
		float rx = nz * lebar * 0.5F;
		float rz = -nx * lebar * 0.5F;
		s.quad(cx - rx + nx * 0.035F, cy + 0.07F, cz - rz + nz * 0.035F, cx - rx - nx * 0.035F, cy + 0.07F, cz - rz - nz * 0.035F, cx + rx - nx * 0.035F, cy + 0.07F,
				cz + rz - nz * 0.035F, cx + rx + nx * 0.035F, cy + 0.07F, cz + rz + nz * 0.035F, 0.0F, 0.0F, 1.0F, 1.0F, 0xFF6A4A2A, light, false);
	}

	/** One glyph quad (a 16 x 16 white texture tinted by {@code argb}) facing {@code (nx, nz)}. */
	private static void glif(String id, float cx, float cy, float cz, float nx, float nz, float ukuran, boolean putar, int argb, int light) {
		KainKota.kotak(Geo.tipe(Kultivator.id(GLIF + id + ".png"), Geo.Jenis.POTONG), cx, cy, cz, nx, nz, ukuran, ukuran, putar, PENUH, argb, light, false);
	}

	/**
	 * The sect emblem as three atlas layers (shape in the field colour, symbol in the charge colour lifted when it would vanish,
	 * rim in the border colour), like the branch board; the atlas is 512 x 384 with 64 px cells.
	 */
	private static void lambang(Lambang l, float cx, float cy, float cz, float nx, float nz, float ukuran, int light) {
		Geo.Sink s = Geo.tipe(LAMBANG, Geo.Jenis.LENGKUNG);
		float shapeU = l.shape() * 64.0F;
		float symU = l.symbol() % 8 * 64.0F;
		float symV = 128.0F + l.symbol() / 8 * 64.0F;
		int field = 0xFF000000 | l.fieldRgb() & 0xFFFFFF;
		int charge = 0xFF000000 | l.chargeRgb() & 0xFFFFFF;
		int border = 0xFF000000 | l.borderRgb() & 0xFFFFFF;
		float e = 0.012F;
		KainKota.kotak(s, cx + nx * e, cy, cz + nz * e, nx, nz, ukuran, ukuran, false, atlas(shapeU, 0.0F), field, light, false);
		KainKota.kotak(s, cx + nx * e * 2, cy, cz + nz * e * 2, nx, nz, ukuran, ukuran, false, atlas(symU, symV), charge, light, false);
		KainKota.kotak(s, cx + nx * e * 3, cy, cz + nz * e * 3, nx, nz, ukuran, ukuran, false, atlas(shapeU, 64.0F), border, light, false);
	}

	private static float[] atlas(float u, float v) {
		return new float[] {u / 512.0F, v / 384.0F, (u + 64.0F) / 512.0F, (v + 64.0F) / 384.0F};
	}

	private PanjiKota() {
	}
}

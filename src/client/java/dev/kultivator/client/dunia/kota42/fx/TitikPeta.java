package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.dunia.kota42.KotaPayloads;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The visual titik the server sent ({@link KotaHidupClient#semuaTitik}), sorted by kind into world-space records the fx read
 * every frame (lanterns, chimneys, banners, street banners in pairs, engraved texts, steaming displays), plus the middle and
 * radius of every place (from all its titik: the lantern wave runs from the edge in toward the middle, 42.9.2). Rebuilt when the
 * set of payloads changes (checked twice a second); empty until the server sends. Layout of {@code data}: {@link TitikKode}.
 * Client thread only.
 */
final class TitikPeta {

	record Lentera(long situs, float x, float y, float z, int hadap, int warna, int jenis, int rumah, float fase, int hash) {
	}

	record Cerobong(long situs, float x, float y, float z, int jenis, int rumah, int hash) {
	}

	/** A cloth, board or door paper: {@code glif} is the glyph texture ids of the titik (0-2). */
	record Kain(long situs, float x, float y, float z, int hadap, int kain, List<String> glif, int rumah, int hash) {
	}

	record Spanduk(long situs, float x, float y, float z, int hadap, boolean b, List<String> glif, int hash) {
	}

	record Pasangan(Spanduk a, Spanduk b) {
	}

	record Teks(long situs, float x, float y, float z, int hadap, boolean nisan, String teks) {
	}

	record Uap(long situs, float x, float y, float z, int pajangan, int hash) {
	}

	/** The middle (x, z) and the radius of a place's titik. */
	record Pusat(float x, float z, float radius) {
	}

	static List<Lentera> lentera = List.of();
	static List<Cerobong> cerobong = List.of();
	static List<Kain> kain = List.of();
	static List<Pasangan> spanduk = List.of();
	static List<Teks> teks = List.of();
	static List<Uap> uap = List.of();
	static Map<Long, Pusat> pusat = Map.of();
	private static int versi = Integer.MIN_VALUE;

	/** Rebuilds when the payload set changed. Called every 10 ticks. */
	static void segarkan() {
		int v = 17;
		for (KotaPayloads.TitikVisual p : KotaHidupClient.semuaTitik()) {
			v = v * 31 + System.identityHashCode(p);
		}
		if (v == versi) {
			return;
		}
		versi = v;
		List<Lentera> ln = new ArrayList<>();
		List<Cerobong> cb = new ArrayList<>();
		List<Kain> kn = new ArrayList<>();
		List<Spanduk> sa = new ArrayList<>();
		List<Spanduk> sb = new ArrayList<>();
		List<Teks> tk = new ArrayList<>();
		List<Uap> up = new ArrayList<>();
		Map<Long, double[]> sum = new HashMap<>();
		for (KotaPayloads.TitikVisual p : KotaHidupClient.semuaTitik()) {
			int ox = ChunkPos.getX(p.chunk()) * 16;
			int oz = ChunkPos.getZ(p.chunk()) * 16;
			for (KotaPayloads.EntriTitik e : p.titik()) {
				float x = ox + e.x() + 0.5F;
				float y = e.y();
				float z = oz + e.z() + 0.5F;
				short d = e.data();
				int hash = mix(Math.round(x * 2.0F), Math.round(y * 2.0F), Math.round(z * 2.0F));
				switch (e.jenis()) {
					case KotaPayloads.TITIK_LENTERA -> {
						ln.add(new Lentera(p.situs(), x, y, z, TitikKode.hadap(d), Math.min(TitikKode.sub(d), 4), Math.min(TitikKode.ekstra(d), 4),
								TitikKode.ref(d), (hash & 1023) / 1023.0F * 6.2831855F, hash));
						tambah(sum, p.situs(), x, z);
					}
					case KotaPayloads.TITIK_CEROBONG -> {
						cb.add(new Cerobong(p.situs(), x, y, z, Math.min(TitikKode.sub(d), 3), TitikKode.ref(d), hash));
						tambah(sum, p.situs(), x, z);
					}
					case KotaPayloads.TITIK_PANJI -> {
						boolean pintu = TitikKode.sub(d) == TitikKode.KAIN_PINTU;
						kn.add(new Kain(p.situs(), x, y, z, TitikKode.hadap(d), Math.min(TitikKode.sub(d), 4),
								glif(p, pintu ? TitikKode.TIDAK_ADA : TitikKode.ref(d)), pintu ? TitikKode.ref(d) : TitikKode.TIDAK_ADA, hash));
						tambah(sum, p.situs(), x, z);
					}
					case KotaPayloads.TITIK_SPANDUK -> {
						Spanduk s = new Spanduk(p.situs(), x, y, z, TitikKode.hadap(d), TitikKode.ekstra(d) == 1, glif(p, TitikKode.ref(d)), hash);
						(s.b() ? sb : sa).add(s);
					}
					case KotaPayloads.TITIK_NISAN, KotaPayloads.TITIK_NAMA_JALAN -> {
						int r = TitikKode.ref(d);
						if (r != TitikKode.TIDAK_ADA && r < p.teks().size()) {
							tk.add(new Teks(p.situs(), x, y, z, TitikKode.hadap(d), e.jenis() == KotaPayloads.TITIK_NISAN, p.teks().get(r)));
						}
					}
					case KotaPayloads.TITIK_PAJANGAN -> {
						int i = TitikKode.pajangan(d);
						if (i >= 0) {
							up.add(new Uap(p.situs(), x, y, z, i, hash));
						}
					}
					default -> {
					}
				}
			}
		}
		Map<Long, Pusat> pc = new HashMap<>();
		for (Map.Entry<Long, double[]> en : sum.entrySet()) {
			double[] s = en.getValue();
			pc.put(en.getKey(), new Pusat((float) (s[0] / s[2]), (float) (s[1] / s[2]), 1.0F));
		}
		// radius = the farthest lantern / chimney / banner from the middle (at least 24 blocks so a hamlet's wave is not instant)
		Map<Long, float[]> rad = new HashMap<>();
		for (Lentera l : ln) {
			jari(pc, rad, l.situs(), l.x(), l.z());
		}
		for (Cerobong c : cb) {
			jari(pc, rad, c.situs(), c.x(), c.z());
		}
		for (Kain k : kn) {
			jari(pc, rad, k.situs(), k.x(), k.z());
		}
		Map<Long, Pusat> out = new HashMap<>();
		for (Map.Entry<Long, Pusat> en : pc.entrySet()) {
			float[] r = rad.get(en.getKey());
			out.put(en.getKey(), new Pusat(en.getValue().x(), en.getValue().z(), Math.max(24.0F, r == null ? 24.0F : r[0])));
		}
		List<Pasangan> pairs = new ArrayList<>();
		boolean[] dipakai = new boolean[sb.size()];
		for (Spanduk a : sa) {
			int best = -1;
			double bd = 28.0D * 28.0D;
			for (int i = 0; i < sb.size(); i++) {
				Spanduk b = sb.get(i);
				if (dipakai[i] || b.situs() != a.situs()) {
					continue;
				}
				double dx = b.x() - a.x();
				double dz = b.z() - a.z();
				double dy = b.y() - a.y();
				double dd = dx * dx + dz * dz + dy * dy * 4.0D;
				if (dd < bd && dd > 4.0D) {
					bd = dd;
					best = i;
				}
			}
			if (best >= 0) {
				dipakai[best] = true;
				pairs.add(new Pasangan(a, sb.get(best)));
			}
		}
		lentera = List.copyOf(ln);
		cerobong = List.copyOf(cb);
		kain = List.copyOf(kn);
		spanduk = List.copyOf(pairs);
		teks = List.copyOf(tk);
		uap = List.copyOf(up);
		pusat = Map.copyOf(out);
	}

	static void bersihkan() {
		versi = Integer.MIN_VALUE;
		lentera = List.of();
		cerobong = List.of();
		kain = List.of();
		spanduk = List.of();
		teks = List.of();
		uap = List.of();
		pusat = Map.of();
	}

	/** The wave rank of a point of place {@code situs}: 0 at the middle, 1 at the edge (gate). */
	static float tepi(long situs, float x, float z) {
		Pusat p = pusat.get(situs);
		if (p == null) {
			return 0.5F;
		}
		float dx = x - p.x();
		float dz = z - p.z();
		return Math.min(1.0F, (float) Math.sqrt(dx * dx + dz * dz) / p.radius());
	}

	static int mix(int a, int b, int c) {
		int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return h & 0x7FFFFFFF;
	}

	/** The place's middle, or null. */
	static @Nullable Pusat pusat(long situs) {
		return pusat.get(situs);
	}

	private static void tambah(Map<Long, double[]> sum, long situs, float x, float z) {
		double[] s = sum.computeIfAbsent(situs, k -> new double[3]);
		s[0] += x;
		s[1] += z;
		s[2] += 1.0D;
	}

	private static void jari(Map<Long, Pusat> pc, Map<Long, float[]> rad, long situs, float x, float z) {
		Pusat p = pc.get(situs);
		if (p == null) {
			return;
		}
		float dx = x - p.x();
		float dz = z - p.z();
		float d = (float) Math.sqrt(dx * dx + dz * dz);
		float[] r = rad.computeIfAbsent(situs, k -> new float[1]);
		r[0] = Math.max(r[0], d);
	}

	/** The glyph ids of titik text {@code ref} ("fu,chun" gives [fu, chun]), at most two, as texture names ({@code fu_jimat} is {@code fu2}). */
	private static List<String> glif(KotaPayloads.TitikVisual p, int ref) {
		if (ref == TitikKode.TIDAK_ADA || ref >= p.teks().size()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(2);
		for (String id : p.teks().get(ref).split(",")) {
			String t = id.trim();
			if (!t.isEmpty() && out.size() < 2) {
				out.add("fu_jimat".equals(t) ? "fu2" : t);
			}
		}
		return out;
	}

	private TitikPeta() {
	}
}

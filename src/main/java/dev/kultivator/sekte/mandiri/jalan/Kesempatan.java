package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Fortune on the way (DESIGN 39.6.9, "Kesempatan"): 10 % per successful journey stage that is asked (20 % in a secret
 * realm). A spirit fruit (+8 % of the bar, never past a bottleneck), a small inheritance (a jade slip of the disciple's
 * own Dao within its grade), a spirit spring (+20 % self-made XP for three days), an enlightenment (Dao comprehension
 * +3), an old master met on the road (comprehension +1.5 and a named story), a treasure (a 300-year herb, a fragment of
 * the Heavenvoid, a spirit-tail feather) or the bad one: a poisoned trap (Qi Deviation 3600 ticks, or wounded).
 */
final class Kesempatan {

	private enum Jenis {
		BUAH, WARISAN, MATA_AIR, PENCERAHAN, AHLI_TUA, HARTA, RACUN
	}

	/** Weights of the seven fortunes (the trap is one in ten). */
	private static final int[] BOBOT = {20, 12, 14, 14, 10, 20, 10};

	static void coba(Tahap.Konteks k, double peluang) {
		Rencana r = k.r();
		RandomSource rnd = k.rnd();
		if (rnd.nextDouble() >= peluang || r.hasil().getIntOr("kesempatan", 0) >= 2) {
			return;
		}
		r.hasil().putInt("kesempatan", r.hasil().getIntOr("kesempatan", 0) + 1);
		int total = 0;
		for (int w : BOBOT) {
			total += w;
		}
		int roll = rnd.nextInt(total);
		Jenis hit = Jenis.RACUN;
		for (Jenis j : Jenis.values()) {
			roll -= BOBOT[j.ordinal()];
			if (roll < 0) {
				hit = j;
				break;
			}
		}
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		EntriHarian.Suasana mood = EntriHarian.Suasana.SENANG;
		switch (hit) {
			case BUAH -> r.hasil().putDouble("buah", r.hasil().getDoubleOr("buah", 0.0D) + 0.08D);
			case WARISAN -> {
				String slip = warisan(r, rnd);
				if (slip == null) {
					Tahap.tambahPaham(r, r.soul(), 1.5D);
					hit = Jenis.PENCERAHAN;
				} else {
					r.barang(r.soul(), "kultivator:jade_slip#" + slip, 1, DaftarBelanja.KE_TAS, 0L, false);
					args.add("kultivator.technique." + slip);
				}
			}
			case MATA_AIR -> r.hasil().putBoolean("berkah", true);
			case PENCERAHAN -> Tahap.tambahPaham(r, r.soul(), 3.0D);
			case AHLI_TUA -> {
				Tahap.tambahPaham(r, r.soul(), 1.5D);
				args.addAll(JalanIds.orang((int) (r.benih() >>> 11), rnd.nextBoolean()));
			}
			case HARTA -> {
				String[] pool = {"kultivator:herb@300", "kultivator:heavenvoid_fragment", "kultivator:spirit_tail_feather"};
				String item = pool[rnd.nextInt(pool.length)];
				r.barang(r.soul(), item, 1, DaftarBelanja.KE_TAS, 0L, false);
				args.add(JalanIds.kunciBarang(item));
			}
			case RACUN -> {
				mood = EntriHarian.Suasana.SEDIH;
				if (rnd.nextBoolean()) {
					r.hasil().putInt("deviasi", 3600);
				} else {
					r.lukai(r.soul(), JenisLuka.TERLUKA);
				}
			}
		}
		String id = hit.name().toLowerCase(java.util.Locale.ROOT);
		r.kisah(JalanIds.k("kisah.kesempatan." + id), args);
		r.kenangan(hit == Jenis.RACUN ? "mandiri.jebakan" : "mandiri.kesempatan");
		Tahap.catat(k, r.soul(), "kesempatan", JalanIds.k("harian.kesempatan." + id), args, mood, hit != Jenis.RACUN);
	}

	/** A teachable slip of the leader's Dao within its grade (tier at most 1 + band / 2), or null. */
	private static String warisan(Rencana r, RandomSource rnd) {
		int daoIndex = r.anggota(r.soul()).getIntOr("dao", -1);
		if (daoIndex < 0 || daoIndex >= Dao.values().length) {
			return null;
		}
		int limit = 1 + r.anggota(r.soul()).getIntOr("band", 0) / 2;
		List<TechniqueSpec> pool = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.ofDao(Dao.values()[daoIndex])) {
			if (spec.teachable() && spec.slipAllowed() && spec.grade().tier() <= limit) {
				pool.add(spec);
			}
		}
		return pool.isEmpty() ? null : pool.get(rnd.nextInt(pool.size())).id();
	}

	private Kesempatan() {
	}
}

package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.dunia.kota42.KotaPayloads;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Kitchen smoke and steam (DESIGN 42.9.2 {@code AsapDapur}). Every {@code cerobong} titik gets a schedule computed by the client
 * from the world clock and the chimney's own hash, so nothing is sent and every client sees the same smoking houses: houses burn
 * their stove 06:00-07:00, 11:30-12:30 and 17:30-19:00 (each house shifted up to a quarter hour), food businesses (baker, tea house,
 * inn) from 06:00 to 22:00, the smith's forge 07:00-18:00 with flying sparks and darker smoke, and a house of mourning stays cold on
 * its first day. Winter smoke is thicker (and denser). The smoke is {@link PartikelKota#asap}: 8 growing frames, rising 0.04 blocks
 * a tick and leaning with the day's wind. Steam rises from hot dishes on display (bowl, noodles, teapot, bun) while the shops
 * are open. Rates per chimney: 0.5 / 2 / 4 puffs a second within 32 / 64 / 96 blocks of the player (Minimal / Normal / Maximal).
 * Client thread only.
 */
final class AsapDapur {

	private static List<TitikPeta.Cerobong> dekat = List.of();
	private static List<TitikPeta.Uap> uapDekat = List.of();
	private static int detak;

	static void tik() {
		var pemain = Bingkai.pemain;
		var level = Bingkai.level;
		if (pemain == null || level == null) {
			return;
		}
		double jarak = Bingkai.tier == 0 ? 32.0D : Bingkai.tier == 1 ? 64.0D : 96.0D;
		if (detak++ % 20 == 0) {
			List<TitikPeta.Cerobong> c = new ArrayList<>();
			for (TitikPeta.Cerobong k : TitikPeta.cerobong) {
				double dx = k.x() - pemain.getX();
				double dz = k.z() - pemain.getZ();
				if (dx * dx + dz * dz <= jarak * jarak) {
					c.add(k);
				}
			}
			dekat = c;
			List<TitikPeta.Uap> u = new ArrayList<>();
			for (TitikPeta.Uap k : TitikPeta.uap) {
				double dx = k.x() - pemain.getX();
				double dz = k.z() - pemain.getZ();
				if (dx * dx + dz * dz <= 24.0D * 24.0D && uapPanas(k.pajangan())) {
					u.add(k);
				}
			}
			uapDekat = u;
		}
		RandomSource rnd = level.getRandom();
		float laju = (Bingkai.tier == 0 ? 0.5F : Bingkai.tier == 1 ? 2.0F : 4.0F) / 20.0F;
		for (TitikPeta.Cerobong k : dekat) {
			KotaPayloads.Suasana s = KotaHidupClient.suasana(k.situs());
			boolean dingin = s != null && s.musim() == 3;
			if (!menyala(k, s)) {
				continue;
			}
			float p = laju * (dingin ? 1.6F : 1.0F) * (k.jenis() == TitikKode.ASAP_USAHA ? 1.3F : 1.0F);
			if (rnd.nextFloat() >= p) {
				continue;
			}
			float tebal = dingin ? 1.75F : 1.0F;
			int rgb = 0xE4E4E2;
			if (k.jenis() == TitikKode.ASAP_PANDAI) {
				rgb = 0x9A9896;
				tebal *= 1.15F;
			}
			PartikelKota.asap(rnd, k.x(), k.y() + 0.1F, k.z(), tebal, rgb);
			if (k.jenis() == TitikKode.ASAP_PANDAI && Bingkai.tier > 0 && rnd.nextInt(3) == 0) {
				// the hammer on the anvil: a short burst of sparks out of the forge's smoke
				for (int i = 0; i < 2 + rnd.nextInt(3); i++) {
					PartikelKota.percik(rnd, k.x(), k.y() + 0.2F, k.z(), (rnd.nextFloat() - 0.5F) * 0.07F, 0.05F + rnd.nextFloat() * 0.05F,
							(rnd.nextFloat() - 0.5F) * 0.07F, 10 + rnd.nextInt(8), 0.07F);
				}
			}
		}
		float uapLaju = (Bingkai.tier == 0 ? 0.04F : 0.09F);
		if (Bingkai.antara(6.0D, 22.0D)) {
			for (TitikPeta.Uap k : uapDekat) {
				if (rnd.nextFloat() < uapLaju) {
					PartikelKota.uap(rnd, k.x(), k.y() + 0.35F, k.z());
				}
			}
		}
	}

	/** Mourning (first day), the clock and the kind decide whether this chimney smokes now. */
	private static boolean menyala(TitikPeta.Cerobong k, KotaPayloads.Suasana s) {
		if (k.jenis() == TitikKode.ASAP_DUKA) {
			return false;
		}
		if (s != null && k.rumah() != TitikKode.TIDAK_ADA) {
			for (KotaPayloads.EntriRumah r : s.rumah()) {
				if (r.rumah() == k.rumah() && r.jenis() == KotaPayloads.RUMAH_DUKA) {
					return false;
				}
			}
		}
		return switch (k.jenis()) {
			case TitikKode.ASAP_USAHA -> Bingkai.antara(6.0D, 22.0D);
			case TitikKode.ASAP_PANDAI -> Bingkai.antara(7.0D, 18.0D);
			default -> {
				double geser = ((k.hash() % 31) - 15) / 60.0D;
				yield Bingkai.antara(6.0D + geser, 7.0D + geser) || Bingkai.antara(11.5D + geser, 12.5D + geser) || Bingkai.antara(17.5D + geser, 19.0D + geser);
			}
		};
	}

	/** The displays that steam: bowl, noodles, teapot, bun. */
	private static boolean uapPanas(int pajangan) {
		return pajangan == 0 || pajangan == 1 || pajangan == 2 || pajangan == 9;
	}

	static void bersihkan() {
		dekat = List.of();
		uapDekat = List.of();
	}

	private AsapDapur() {
	}
}

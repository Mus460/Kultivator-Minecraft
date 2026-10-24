package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.sekte.mandiri.api.GildaMurid;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The default guild board for disciples (contract C39.K4, DESIGN 39.6.8): every town posts six errands a day, seeded by
 * (town, day), from rank E to the town's top rank. A mission's danger tier is its rank plus a little luck; the pay is
 * {@code 6 x (tier + 1) x rank factor} NB (E 0.6, D 0.8, C 1.0, B 1.3, A 1.6, S 2.0), a failure pays nothing. Stage
 * success follows the expedition rule {@code clamp(0.45 + 0.12 (P - (tier - 1)), 0.10, 0.95)} in {@link Tahap}. Titles are
 * lang keys {@code kultivator.sekte.mandiri.jalan.misi.<jenis>.<n>}. 38EK may replace it with {@code MandiriApi.setGilda}.
 */
public final class GildaBawaan implements GildaMurid {

	public static final GildaBawaan INSTANCE = new GildaBawaan();

	/** Errand kinds and how many title variants each has in the lang fragment. */
	static final String[] JENIS = {"antar", "kawal", "buru", "herba", "usir", "cari"};
	static final int JUDUL = 4;
	/** Pay factor by rank E..S. */
	static final double[] FAKTOR = {0.6D, 0.8D, 1.0D, 1.3D, 1.6D, 2.0D};
	/** Errands posted per town and day. */
	static final int PER_HARI = 6;

	private static @Nullable MinecraftServer server;

	static void server(@Nullable MinecraftServer value) {
		server = value;
	}

	private GildaBawaan() {
	}

	@Override
	public List<MisiMurid> papan(long town, int pangkat) {
		MinecraftServer srv = server;
		long hari = srv == null ? 0L : Kota.day(srv);
		List<MisiMurid> out = new ArrayList<>(PER_HARI);
		for (int i = 0; i < PER_HARI; i++) {
			long z = PasarBawaan.benih(town, hari, "gilda#" + i);
			// half of the board is the easy errands E-C, the other half any rank of the town
			int rank = (int) (Math.floorMod(z, 6L) <= 2L ? Math.floorMod(z >>> 8, 3L) : Math.floorMod(z >>> 8, 6L));
			if (rank > pangkat) {
				continue;
			}
			String jenis = JENIS[(int) Math.floorMod(z >>> 16, (long) JENIS.length)];
			int tier = Math.max(1, rank + 1 + (Math.floorMod(z >>> 24, 4L) == 0 ? 1 : 0));
			long upah = Math.max(1L, Math.round(6.0D * (tier + 1) * FAKTOR[rank]));
			String judul = JalanIds.k("misi." + jenis + "." + Math.floorMod(z >>> 32, (long) JUDUL));
			out.add(new MisiMurid(Long.toHexString(town) + ":" + hari + ":" + i, rank, tier, jenis, upah, judul));
		}
		return out;
	}

	@Override
	public long upah(MisiMurid misi, boolean sukses) {
		return sukses && misi != null ? misi.upahDasar() : 0L;
	}
}

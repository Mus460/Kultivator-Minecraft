package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.StokKota;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * Town prosperity {@code K} 0-100 (DESIGN 42.9.4, contract C42.F1): every Hari Warga the day value
 * {@code 0.25 x (50 + mood/2) + 0.25 x economy + 0.15 x safety + 0.15 x trade + 0.1 x health + 0.1 x low tax} is computed
 * and the published value is the mean of the last seven days plus the weekly low-tax bonus ({@code PajakKota.bonusMakmur},
 * at most +10 kept). Terms (each 0-100):
 * <ul>
 *   <li>mood: {@code WargaApi.suasanaTempat} (-100..100) as {@code 50 + mood/2};</li>
 *   <li>economy: last week's Kas Kota net (taxes and sinks in, wages and plots out) per resident, 50 = even;</li>
 *   <li>safety: 100 minus raids (25 each), crimes (6 each) and real damage still waiting (1 per 20 blocks) of the week;
 *       a protection pact +10;</li>
 *   <li>trade: how full the town's twelve stocks are ({@code StokKota}: 100 = normal), caravans and contracts fill them;</li>
 *   <li>health: mean resident health;</li>
 *   <li>low tax: 5 % = 100 down to 20 % = 0.</li>
 * </ul>
 * Stored in {@code KotaTumbuhData.situs("makmur", situs)}: {@code h} the last 7 day values, {@code n} the published value,
 * {@code d} the last day's terms, {@code b} the bonus, {@code w}/{@code ser}/{@code jah} the week's raid and crime counters,
 * {@code ov}/{@code ovs} a test override ({@code uji kota_hidup tumbuh makmur <n>}) and the day it ends.
 */
public final class Kemakmuran {

	/** Neutral prosperity (no growth, no decline). */
	public static final int NETRAL = 50;
	/** Day values kept for the mean. */
	public static final int HARI = 7;
	/** Term names in the order of {@link #rincian} (lang {@code kultivator.kota42.tumbuh.makmur.<nama>}). */
	public static final String[] SUKU = {"suasana", "ekonomi", "keamanan", "dagang", "kesehatan", "pajak"};
	private static final double[] BOBOT = {0.25D, 0.25D, 0.15D, 0.15D, 0.10D, 0.10D};

	/** The prosperity of place {@code situs}, 0-100. */
	public static int nilai(long situs) {
		MinecraftServer server = KotaHidup.server();
		return server == null ? NETRAL : nilai(server, situs);
	}

	public static int nilai(MinecraftServer server, long situs) {
		CompoundTag c = KotaTumbuhData.get(server).bagian(KotaTumbuhData.MAKMUR).getCompoundOrEmpty("kota_" + situs);
		if (c.contains("ov") && c.getLongOr("ovs", 0L) >= KotaHidup.hari(server)) {
			return c.getIntOr("ov", NETRAL);
		}
		return c.getIntOr("n", NETRAL);
	}

	/** -1 falling, 0 steady, 1 rising over the kept days. */
	public static int tren(MinecraftServer server, long situs) {
		int[] h = data(server, situs).getIntArray("h").orElse(new int[0]);
		if (h.length < 2) {
			return 0;
		}
		int d = h[h.length - 1] - h[0];
		return d > 3 ? 1 : d < -3 ? -1 : 0;
	}

	/** The last day's six terms 0-100 in {@link #SUKU} order (all {@link #NETRAL} before the first day). */
	public static int[] rincian(MinecraftServer server, long situs) {
		int[] d = data(server, situs).getIntArray("d").orElse(new int[0]);
		if (d.length != SUKU.length) {
			int[] out = new int[SUKU.length];
			java.util.Arrays.fill(out, NETRAL);
			return out;
		}
		return d;
	}

	/** Test override for {@code hari} world days ({@code n < 0} clears). */
	public static void timpa(MinecraftServer server, long situs, int n, int hari) {
		CompoundTag c = data(server, situs);
		if (n < 0) {
			c.remove("ov");
			c.remove("ovs");
		} else {
			c.putInt("ov", Mth.clamp(n, 0, 100));
			c.putLong("ovs", KotaHidup.hari(server) + Math.max(1, hari));
		}
		KotaTumbuhData.get(server).ubah();
	}

	static CompoundTag data(MinecraftServer server, long situs) {
		return KotaTumbuhData.get(server).situs(KotaTumbuhData.MAKMUR, situs);
	}

	// ================================================================== counters (events)

	private static CompoundTag minggu(MinecraftServer server, long situs) {
		CompoundTag c = data(server, situs);
		long w = Math.floorDiv(KotaHidup.hari(server), 7L);
		if (c.getLongOr("w", Long.MIN_VALUE) != w) {
			c.putLong("w", w);
			c.putInt("ser", 0);
			c.putInt("jah", 0);
		}
		return c;
	}

	/** A raid began on the town (42E bus). */
	static void serbuan(MinecraftServer server, long situs) {
		CompoundTag c = minggu(server, situs);
		c.putInt("ser", c.getIntOr("ser", 0) + 1);
		KotaTumbuhData.get(server).ubah();
	}

	/** A crime was seen in the town (38 events). */
	static void kejahatan(MinecraftServer server, long situs) {
		CompoundTag c = minggu(server, situs);
		c.putInt("jah", c.getIntOr("jah", 0) + 1);
		KotaTumbuhData.get(server).ubah();
	}

	/** Raids this week (the need for a watchtower). */
	static int serbuanMinggu(MinecraftServer server, long situs) {
		return minggu(server, situs).getIntOr("ser", 0);
	}

	// ================================================================== the day

	/** Hari Warga: computes the day value and the published mean. */
	static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		long situs = s.id;
		CompoundTag c = minggu(server, situs);
		int[] t = new int[SUKU.length];
		t[0] = Mth.clamp(50 + WargaApi.suasanaTempat(situs) / 2, 0, 100);
		t[1] = ekonomi(server, s, hari);
		int safety = 100 - 25 * c.getIntOr("ser", 0) - 6 * c.getIntOr("jah", 0) - PerbaikanApi.rusakTerbuka(situs) / 20;
		if (PaktaLindung.pelindung(situs) != null) {
			safety += 10;
		}
		t[2] = Mth.clamp(safety, 0, 100);
		t[3] = dagang(server, situs);
		int health = 0;
		int n = 0;
		for (Warga w : s.warga) {
			if (w.status == Warga.Status.HIDUP) {
				health += w.kesehatan;
				n++;
			}
		}
		t[4] = n == 0 ? NETRAL : Mth.clamp(health / n, 0, 100);
		int tarif = PajakKota.aktif() ? PajakKota.tarif(server, situs) : 10;
		t[5] = Mth.clamp(Math.round((PajakKota.TARIF_MAKS - tarif) * 100.0F / (PajakKota.TARIF_MAKS - PajakKota.TARIF_MIN)), 0, 100);
		double k = 0.0D;
		for (int i = 0; i < t.length; i++) {
			k += BOBOT[i] * t[i];
		}
		int[] h = c.getIntArray("h").orElse(new int[0]);
		int[] next = new int[Math.min(HARI, h.length + 1)];
		System.arraycopy(h, Math.max(0, h.length + 1 - HARI), next, 0, next.length - 1);
		next[next.length - 1] = (int) Math.round(k);
		if (Math.floorMod(hari, 7L) == 0L) {
			c.putInt("b", Mth.clamp(c.getIntOr("b", 0) + PajakKota.bonusMakmur(situs), 0, 10));
		}
		int sum = 0;
		for (int v : next) {
			sum += v;
		}
		c.putIntArray("h", next);
		c.putIntArray("d", t);
		c.putInt("n", Mth.clamp(Math.round((float) sum / next.length) + c.getIntOr("b", 0), 0, 100));
		KotaTumbuhData.get(server).ubah();
	}

	private static int ekonomi(MinecraftServer server, WargaData.Situs s, long hari) {
		long week = Math.floorDiv(hari, 7L) - 1L;
		long in = 0L;
		long out = 0L;
		for (Map.Entry<String, Long> e : KasKota.buku(server, s.id, week, false).entrySet()) {
			in += e.getValue();
		}
		for (Map.Entry<String, Long> e : KasKota.buku(server, s.id, week, true).entrySet()) {
			out += e.getValue();
		}
		int pop = Math.max(1, s.warga.size());
		// 2 NB a resident a week of surplus is a rich town (+50), the same deficit a poor one
		return Mth.clamp((int) Math.round(50.0D + (in - out) * 25.0D / pop), 0, 100);
	}

	private static int dagang(MinecraftServer server, long situs) {
		int sum = 0;
		int n = 0;
		for (Kategori k : Kategori.values()) {
			sum += StokKota.stok(server, situs, k);
			n++;
		}
		return n == 0 ? NETRAL : Mth.clamp(sum / n / 2, 0, 100);
	}

	private Kemakmuran() {
	}
}

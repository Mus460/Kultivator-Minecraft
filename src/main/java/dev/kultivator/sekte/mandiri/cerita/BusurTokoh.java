package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.HasilJalan;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.Tokoh;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * The small arcs of DESIGN 39.8.3, moved only by real events of the other modules:
 * <ul>
 * <li><b>PEDAGANG langganan</b>: the third purchase of one disciple in one town makes the stall keeper a regular
 * ({@code MandiriBus.BELANJA}); later visits greet the disciple by name, warm the keeper (+3) and, once a week, the keeper
 * "kept something back" (a diary line; the 5 % discount itself belongs to the market, C39.K1).</li>
 * <li><b>PENCURI</b>: a pickpocket who got away ({@code KOTA_PERISTIWA "copet_lolos"}) is remembered by town; caught later
 * in the same town ({@code "copet"}) the arc closes ("akhirnya!").</li>
 * <li><b>PENGEMBARA</b>: a wanderer met at a tea house or on the road ({@code "pengembara"}) talks three times, then
 * moves on.</li>
 * <li><b>AHLI_TUA</b>: an old master ({@code "ahli_tua"}) teaches once; a second meeting is a rare Dao hint.</li>
 * <li><b>KEPALA_DESA</b>: the head of a village a disciple helped ({@code JALAN_TIBA} of a DESA journey) asks again.</li>
 * </ul>
 * Rival and sweetheart arcs are {@link BusurSaingan} and {@link BusurAsmara}.
 */
final class BusurTokoh {

	/** Purchases in one town before the keeper becomes a regular. */
	static final int LANGGANAN = 3;

	/** {@code MandiriBus.BELANJA}. */
	static void belanja(MinecraftServer server, UUID owner, UUID soul, long town, String itemId, int n, long harga) {
		if (town == 0L) {
			return;
		}
		String situs = "kota:" + town;
		long hari = Cerita.hari(server);
		Tokoh keeper = DaftarTokoh.cari(server, owner, Tokoh.Peran.PEDAGANG, situs);
		if (keeper == null) {
			CompoundTag counts = Cerita.bagian(server, owner, "langganan");
			String key = soul + "@" + town;
			int seen = counts.getIntOr(key, 0) + 1;
			counts.putInt(key, seen);
			if (seen < LANGGANAN) {
				return;
			}
			counts.remove(key);
			keeper = DaftarTokoh.baru(server, owner, Tokoh.Peran.PEDAGANG, situs, "ramah", soul, hari, 20, town ^ soul.getLeastSignificantBits());
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pedagang.kenal", EntriHarian.Suasana.SENANG, false, keeper.nama(),
					DaftarTokoh.argKota(town), "i:" + itemId);
			return;
		}
		boolean first = !keeper.sikap().containsKey(soul);
		long lastWeekly = Cerita.bagian(server, owner, "simpanan").getLongOr(keeper.id(), -99L);
		keeper = keeper.dijumpai(hari, situs).ubahSikap(soul, first ? 15 : 3);
		DaftarTokoh.simpan(server, owner, keeper);
		if (first) {
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pedagang.kenal", EntriHarian.Suasana.SENANG, false, keeper.nama(),
					DaftarTokoh.argKota(town), "i:" + itemId);
		} else if (hari - lastWeekly >= 7) {
			Cerita.bagian(server, owner, "simpanan").putLong(keeper.id(), hari);
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pedagang.simpan", EntriHarian.Suasana.SENANG, false, keeper.nama(),
					DaftarTokoh.argKota(town), "i:" + itemId);
		} else if (keeper.jumpa() % 3 == 0) {
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pedagang.sapa", EntriHarian.Suasana.TENANG, false, keeper.nama(),
					DaftarTokoh.argKota(town), "n:" + harga);
		}
	}

	/** {@code MandiriBus.KOTA_PERISTIWA}; returns true when an arc of this class took it. */
	static boolean kota(MinecraftServer server, UUID owner, UUID soul, long town, String jenis) {
		String situs = "kota:" + town;
		long hari = Cerita.hari(server);
		switch (jenis) {
			case "copet_lolos" -> {
				Tokoh thief = DaftarTokoh.cari(server, owner, Tokoh.Peran.PENCURI, situs);
				if (thief == null) {
					thief = DaftarTokoh.baru(server, owner, Tokoh.Peran.PENCURI, situs, "licik", soul, hari, -30, town ^ hari);
				} else {
					thief = thief.dijumpai(hari, situs).ubahSikap(soul, -10);
					DaftarTokoh.simpan(server, owner, thief);
				}
				Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pencuri.lolos", EntriHarian.Suasana.KESAL, false, thief.nama(),
						DaftarTokoh.argKota(town));
				return true;
			}
			case "copet" -> {
				Tokoh thief = DaftarTokoh.cari(server, owner, Tokoh.Peran.PENCURI, situs);
				if (thief == null) {
					return false;
				}
				DaftarTokoh.lupakan(server, owner, thief.id());
				Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pencuri.tertangkap", EntriHarian.Suasana.BANGGA, true, thief.nama(),
						DaftarTokoh.argKota(town));
				return true;
			}
			case "pengembara" -> {
				Tokoh w = DaftarTokoh.dikenal(server, owner, Tokoh.Peran.PENGEMBARA, soul);
				if (w == null) {
					w = DaftarTokoh.baru(server, owner, Tokoh.Peran.PENGEMBARA, situs, "tenang", soul, hari, 10, soul.getMostSignificantBits() ^ hari);
					Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pengembara.kenal", EntriHarian.Suasana.TENANG, false, w.nama(),
							DaftarTokoh.argKota(town));
					return true;
				}
				w = w.dijumpai(hari, situs).ubahSikap(soul, 8);
				if (w.jumpa() >= 3) {
					DaftarTokoh.lupakan(server, owner, w.id());
					Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pengembara.pamit", EntriHarian.Suasana.SEDIH, false, w.nama(),
							DaftarTokoh.argKota(town));
				} else {
					DaftarTokoh.simpan(server, owner, w);
					Cerita.tulis(server, owner, soul, "tokoh", "tokoh.pengembara.kabar", EntriHarian.Suasana.SENANG, false, w.nama(),
							DaftarTokoh.argKota(town));
				}
				return true;
			}
			case "ahli_tua" -> {
				Tokoh m = DaftarTokoh.dikenal(server, owner, Tokoh.Peran.AHLI_TUA, soul);
				if (m == null) {
					m = DaftarTokoh.baru(server, owner, Tokoh.Peran.AHLI_TUA, situs, "tenang", soul, hari, 30, soul.getLeastSignificantBits() ^ town);
					Cerita.tulis(server, owner, soul, "tokoh", "tokoh.ahli_tua.ajar", EntriHarian.Suasana.BANGGA, false, m.nama(),
							DaftarTokoh.argKota(town));
				} else {
					DaftarTokoh.lupakan(server, owner, m.id());
					Cerita.tulis(server, owner, soul, "tokoh", "tokoh.ahli_tua.dao", EntriHarian.Suasana.BANGGA, true, m.nama(),
							DaftarTokoh.argKota(town));
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** {@code MandiriBus.JALAN_TIBA}: a village journey makes its head a recurring character. */
	static void tiba(MinecraftServer server, UUID owner, UUID soul, HasilJalan hasil) {
		if (hasil.jenis() != JenisJalan.DESA || hasil.tujuan().isEmpty()) {
			return;
		}
		String situs = "desa:" + hasil.tujuan();
		long hari = Cerita.hari(server);
		Tokoh head = DaftarTokoh.cari(server, owner, Tokoh.Peran.KEPALA_DESA, situs);
		if (head == null) {
			head = DaftarTokoh.baru(server, owner, Tokoh.Peran.KEPALA_DESA, situs, "ramah", soul, hari, 25, hasil.tujuan().hashCode() * 31L);
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.desa.kenal", EntriHarian.Suasana.SENANG, false, head.nama());
			return;
		}
		head = head.dijumpai(hari, situs).ubahSikap(soul, hasil.sukses() ? 10 : 2);
		DaftarTokoh.simpan(server, owner, head);
		Cerita.tulis(server, owner, soul, "tokoh", hasil.sukses() ? "tokoh.desa.terima_kasih" : "tokoh.desa.minta", EntriHarian.Suasana.SENANG,
				false, head.nama());
	}

	private BusurTokoh() {
	}
}

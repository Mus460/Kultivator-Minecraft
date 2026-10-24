package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.MandiriData;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The disciple's purse (DESIGN 39.6.4, 39.6.11): 39C's money book behind {@code MandiriApi.setKas}, the earning rules
 * (at most {@code hasilMaksHariPerBand x (band + 1)} NB a day, the rest to the hall pool; the loyal hand 30 % of their
 * earnings to the pool, a merchant who reached WEALTH 50 %, the cunning nothing) and the paid spending of a journey.
 * Every change goes through {@code MandiriApi.ubahDompet}, the only purse writer; nothing here makes money.
 * <p>
 * The money book needs the hall and the soul of a {@code DiscipleData}: loaded bodies are remembered on their tick
 * ({@link #ingat}); the return handler of a journey works on the stored tablet data and names them with
 * {@link #dengan}. One hall pool point is 2 NB ({@code Harga.NB_PER_PK}).
 */
public final class Dompet {

	/** Who a {@code DiscipleData} belongs to. */
	private record Pemilik(UUID owner, UUID soul, long hari) {
	}

	private static final Map<DiscipleData, Pemilik> DIMUAT = new IdentityHashMap<>();
	private static final ThreadLocal<Pemilik> KONTEKS = new ThreadLocal<>();
	private static @Nullable MinecraftServer server;

	/** 39C's money book: every purse change into {@code MandiriData.kas}. */
	static final MandiriApi.Kas KAS = (data, delta, saldo, sebab) -> {
		MinecraftServer srv = server;
		Pemilik who = KONTEKS.get();
		if (who == null) {
			who = DIMUAT.get(data);
		}
		if (srv == null || who == null) {
			return;
		}
		MandiriData.get(srv).catatKas(who.owner(), who.soul(), who.hari(), delta, sebab);
	};

	static void server(@Nullable MinecraftServer value) {
		server = value;
		if (value == null) {
			DIMUAT.clear();
		}
	}

	/** Remembers whose data a loaded body carries (its tick20). */
	static void ingat(DiscipleEntity murid, long hari) {
		if (murid.ownerId() != null) {
			DIMUAT.put(murid.data(), new Pemilik(murid.ownerId(), murid.soulId(), hari));
		}
	}

	/** Forgets bodies that are gone (every few minutes; their tick20 adds them again). */
	static void lupakanSemua() {
		DIMUAT.clear();
	}

	/** Runs {@code work} with {@code data} known as {@code owner}'s {@code soul} (stored data of an away disciple). */
	static void dengan(UUID owner, UUID soul, long hari, Runnable work) {
		Pemilik before = KONTEKS.get();
		KONTEKS.set(new Pemilik(owner, soul, hari));
		try {
			work.run();
		} finally {
			if (before == null) {
				KONTEKS.remove();
			} else {
				KONTEKS.set(before);
			}
		}
	}

	// ================================================================== spending

	/** NB in the purse. */
	public static long isi(DiscipleData data) {
		return MandiriApi.dompet(data);
	}

	/** The reserve kept for prio 2-3 purchases: {@code 10 x (realm + 1)} NB. */
	public static long cadangan(DiscipleData data) {
		return 10L * (data.realm().ordinal() + 1L);
	}

	/**
	 * Pays {@code nb} from the purse (39.6.11 rule 2: before anything is made); false and nothing changed when the purse
	 * holds less.
	 */
	public static boolean bayar(DiscipleData data, long nb, String sebab) {
		if (nb <= 0L) {
			return true;
		}
		if (isi(data) < nb) {
			return false;
		}
		long applied = MandiriApi.ubahDompet(data, -nb, sebab);
		return applied == -nb;
	}

	// ================================================================== earning

	/**
	 * Earned money (missions, sold work, road rewards): today's cap per band, the hall's share by character, the purse
	 * limit; whatever does not go to the purse goes to the hall pool. Returns the NB that reached the purse.
	 */
	public static long terima(DiscipleData data, UUID owner, long hari, long nb, String sebab) {
		if (nb <= 0L || data == null || owner == null) {
			return 0L;
		}
		long cap = (long) MandiriConfig.get().hasilMaksHariPerBand * (data.realm().ordinal() + 1L);
		CompoundTag own = data.extraSection(JalanIds.EXTRA);
		CompoundTag hasil = own.getCompound("hasil").orElse(null);
		if (hasil == null || hasil.getLongOr("hari", -1L) != hari) {
			hasil = new CompoundTag();
			hasil.putLong("hari", hari);
			hasil.putLong("nb", 0L);
			own.put("hasil", hasil);
		}
		long today = hasil.getLongOr("nb", 0L);
		long room = Math.max(0L, cap - today);
		long kept = Math.min(room, nb);
		long toHall = nb - kept;
		int share = setoranPersen(data);
		long deposit = kept * share / 100L;
		kept -= deposit;
		toHall += deposit;
		long applied = kept > 0L ? MandiriApi.ubahDompet(data, kept, sebab) : 0L;
		toHall += kept - applied;
		hasil.putLong("nb", today + applied);
		MandiriApi.tambahStatistik(data, MandiriIds.S_HASIL_NB, applied);
		if (toHall >= 2L) {
			Halls.addPool(owner, (int) Math.min(Integer.MAX_VALUE, toHall / 2L));
			MinecraftServer srv = server;
			UUID soul = soulOf(data);
			if (srv != null && soul != null) {
				MandiriData.get(srv).catatKas(owner, soul, hari, -toHall, "setor");
			}
		}
		return applied;
	}

	/** The hall's share of earnings (39.6.4): loyal 30 %, a merchant with WEALTH reached 50 %, the cunning 0 %. */
	static int setoranPersen(DiscipleData data) {
		boolean licik = SekteApi.hati().has(data, Sifat.LICIK);
		if (licik) {
			return 0;
		}
		if (MandiriApi.jalan(data) == JalanHidup.PEDAGANG && citaTercapai(data, JenisCita.KEKAYAAN)) {
			return 50;
		}
		return SekteApi.hati().has(data, Sifat.SETIA) ? 30 : 0;
	}

	private static boolean citaTercapai(DiscipleData data, JenisCita jenis) {
		ListTag done = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_CITA_SELESAI);
		for (int i = 0; i < done.size(); i++) {
			CompoundTag one = done.getCompoundOrEmpty(i);
			if (jenis.name().equalsIgnoreCase(one.getStringOr("jenis", "")) || jenis.id().equals(one.getStringOr("jenis", ""))) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable UUID soulOf(DiscipleData data) {
		Pemilik who = KONTEKS.get();
		if (who == null) {
			who = DIMUAT.get(data);
		}
		return who == null ? null : who.soul();
	}

	private Dompet() {
	}
}

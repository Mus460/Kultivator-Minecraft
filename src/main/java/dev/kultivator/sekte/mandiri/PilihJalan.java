package dev.kultivator.sekte.mandiri;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.Kenangan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Choosing and changing the life path (DESIGN 39.3.2):
 * <pre>
 * skor(j) = 1.0 + traits (+0.6 matching, -0.4 the opposite end) + talent (+1.0) + Dao (+1.2, root of the same element +0.5)
 *         + memories (+0.15 x the weights of matching kinds, at most +-1.5) + habit (+0.02 x hours of the path's own
 *         activities in about ten days, at most +1.0) + the master's push (+0.8) + event bonuses (39.3.5) + inertia (+0.7)
 * </pre>
 * Checked when a disciple is first seen (a fresh recruit draws by {@code skor^2} so a hall is varied), when it awakens
 * (Mortal to Qi Refining), reaches Foundation or chooses a Dao, every ten days and after big events. A new path is taken
 * only when it beats the current one by more than 0.5; the change writes the memory {@code mandiri.jalan_ganti} (+3), a
 * diary page in the disciple's own words, a notice to the master and the {@code JALAN_GANTI} event, and updates the look
 * ({@code SosokApi.setJalan}). The IBLIS overlay is {@link JalanIblis}'s; while it holds, this check waits.
 */
public final class PilihJalan {

	/** The traits that point to a path (39.3.1, column "Syarat skor terkuat"). */
	static Sifat[] sifat(JalanHidup j) {
		return switch (j) {
			case PEDANG, PETUALANG -> new Sifat[]{Sifat.BERANI, Sifat.AMBISIUS};
			case ALKEMIS -> new Sifat[]{Sifat.RAJIN, Sifat.TENANG};
			case FORMASI -> new Sifat[]{Sifat.TENANG, Sifat.RAJIN};
			case PERTAPA -> new Sifat[]{Sifat.TENANG, Sifat.RENDAH_HATI};
			case PEMBURU -> new Sifat[]{Sifat.BERANI, Sifat.PENDENDAM};
			case PEDAGANG -> new Sifat[]{Sifat.LICIK, Sifat.AMBISIUS};
			case CENDEKIA -> new Sifat[]{Sifat.RAJIN, Sifat.RENDAH_HATI};
			case PENJAGA -> new Sifat[]{Sifat.SETIA, Sifat.BERANI};
			case IBLIS -> new Sifat[0];
		};
	}

	/** The talent bit of {@code Talents} that points to a path, or 0. */
	static int bakat(JalanHidup j) {
		return switch (j) {
			case PEDANG -> Talents.INDRA_PEDANG;
			case ALKEMIS -> Talents.BAKAT_PIL;
			case PERTAPA -> Talents.HATI_GIGIH;
			case PEMBURU -> Talents.DARAH_DEWA_KUNO;
			default -> 0;
		};
	}

	static boolean daoCocok(JalanHidup j, @Nullable Dao dao) {
		if (dao == null) {
			return false;
		}
		return switch (j) {
			case PEDANG -> dao == Dao.SWORD;
			case ALKEMIS -> dao == Dao.FIRE || dao == Dao.LIFE;
			case FORMASI -> dao == Dao.SPACE;
			case PERTAPA -> dao == Dao.LIFE || dao == Dao.SPACE;
			case PETUALANG -> dao == Dao.LIGHTNING || dao == Dao.SHADOW;
			case PEMBURU -> dao == Dao.BODY;
			case PENJAGA -> dao == Dao.BODY || dao == Dao.ICE;
			case CENDEKIA -> dao == Dao.SPACE || dao == Dao.ICE;
			case PEDAGANG -> dao == Dao.SHADOW;
			case IBLIS -> false;
		};
	}

	/** Root elements that lean toward a path (39.3.1: Metal for the sword, Fire/Wood for pills, Space/Earth for arrays ...). */
	static int akar(JalanHidup j) {
		return switch (j) {
			case PEDANG -> Element.METAL.bit();
			case ALKEMIS -> Element.FIRE.bit() | Element.WOOD.bit();
			case FORMASI -> Element.SPACE.bit() | Element.EARTH.bit();
			case PERTAPA -> Element.WATER.bit() | Element.ICE.bit();
			case PETUALANG -> Element.WIND.bit() | Element.LIGHTNING.bit();
			case PEMBURU, PENJAGA -> Element.EARTH.bit();
			case CENDEKIA -> Element.WATER.bit();
			case PEDAGANG -> Element.METAL.bit() | Element.WATER.bit();
			case IBLIS -> 0;
		};
	}

	/** Memory kind prefixes that belong to a path ({@code Ingatan.ofKind}). */
	static String[] kenangan(JalanHidup j) {
		return switch (j) {
			case PEDANG -> new String[]{"turnamen.", "hubungan.duel", "mandiri.pedang"};
			case ALKEMIS -> new String[]{"kebun.", "mandiri.alkemis", "mandiri.meramu"};
			case FORMASI -> new String[]{"mandiri.formasi", "mandiri.jimat"};
			case PERTAPA -> new String[]{"mandiri.pertapa", "mandiri.tutup", "paviliun.upacara"};
			case PETUALANG -> new String[]{"misi.", "mandiri.jalan", "mandiri.petualang"};
			case PEMBURU -> new String[]{"mandiri.pemburu", "mandiri.buru"};
			case PEDAGANG -> new String[]{"mandiri.pedagang", "mandiri.belanja", "mandiri.kota"};
			case CENDEKIA -> new String[]{"paviliun.", "mandiri.cendekia", "mandiri.ajar"};
			case PENJAGA -> new String[]{"mandiri.penjaga", "mandiri.jaga", "tetua.", "kepribadian.setia"};
			case IBLIS -> new String[0];
		};
	}

	/** The activities that make a path a habit (39.3.2 "murid menjadi apa yang ia kerjakan"). */
	public static boolean khas(JalanHidup j, Kegiatan k) {
		return switch (j) {
			case PEDANG -> k == Kegiatan.LATIH_JURUS || k == Kegiatan.TANDING;
			case ALKEMIS -> k == Kegiatan.MERAMU || k == Kegiatan.KEBUN;
			case FORMASI -> k == Kegiatan.JIMAT || k == Kegiatan.JAGA_FORMASI;
			case PERTAPA -> k == Kegiatan.MEDITASI || k == Kegiatan.TUTUP_DIRI || k == Kegiatan.MERENUNG;
			case PETUALANG, PEMBURU -> k == Kegiatan.PERJALANAN;
			case PEDAGANG -> k == Kegiatan.PERJALANAN || k == Kegiatan.SOSIAL;
			case CENDEKIA -> k == Kegiatan.BELAJAR || k == Kegiatan.MENGAJAR;
			case PENJAGA -> k == Kegiatan.JAGA_GERBANG || k == Kegiatan.JAGA_FORMASI;
			case IBLIS -> false;
		};
	}

	// ================================================================== scores

	/** The score of every path (index = ordinal; IBLIS is never scored) and the reasons of each. */
	public record Nilai(double[] skor, List<List<String>> alasan) {
	}

	public static Nilai nilai(DiscipleData data, long hari) {
		HatiService hati = SekteApi.hati();
		CompoundTag mandiri = MandiriApi.bagian(data);
		JalanHidup sekarang = MandiriApi.jalan(data);
		CompoundTag kebiasaan = mandiri.getCompoundOrEmpty(MandiriIds.K_KEBIASAAN);
		CompoundTag bonus = mandiri.getCompoundOrEmpty(MandiriIds.K_JALAN_BONUS);
		ListTag dorong = mandiri.getListOrEmpty(MandiriIds.K_JALAN_DORONG);
		List<Kenangan> ingatan = Ingatan.of(data);
		double[] skor = new double[JalanHidup.values().length];
		List<List<String>> alasan = new ArrayList<>();
		for (JalanHidup j : JalanHidup.values()) {
			List<String> why = new ArrayList<>();
			alasan.add(why);
			if (!j.pilihanAwal()) {
				skor[j.ordinal()] = -99.0D;
				continue;
			}
			double s = 1.0D;
			for (Sifat sifat : sifat(j)) {
				if (hati.has(data, sifat)) {
					s += 0.6D;
					why.add("sifat." + sifat.id());
				} else if (hati.has(data, sifat.opposite())) {
					s -= 0.4D;
				}
			}
			int bit = bakat(j);
			if (bit != 0 && Talents.hasTalent(data, bit)) {
				s += 1.0D;
				why.add("bakat." + j.id());
			}
			if (daoCocok(j, data.dao())) {
				s += 1.2D;
				why.add("dao." + data.dao().id());
			}
			if ((data.rootElements() & akar(j)) != 0) {
				s += 0.5D;
				why.add("akar");
			}
			double mem = 0.0D;
			for (Kenangan k : ingatan) {
				for (String prefix : kenangan(j)) {
					if (k.kind().startsWith(prefix)) {
						mem += k.weight();
						break;
					}
				}
			}
			mem = Math.max(-1.5D, Math.min(1.5D, 0.15D * mem));
			if (mem > 0.2D) {
				why.add("kenangan");
			}
			s += mem;
			double habit = Math.min(1.0D, 0.02D * kebiasaan.getDoubleOr(j.id(), 0.0D));
			if (habit > 0.2D) {
				why.add("kebiasaan");
			}
			s += habit;
			for (int i = 0; i < dorong.size(); i++) {
				if (j.id().equals(dorong.getStringOr(i, ""))) {
					s += 0.8D;
					why.add("guru");
					break;
				}
			}
			s += bonus.getDoubleOr(j.id(), 0.0D);
			if (j == sekarang) {
				s += 0.7D;
			}
			skor[j.ordinal()] = s;
		}
		return new Nilai(skor, alasan);
	}

	// ================================================================== choosing

	/**
	 * Checks the path of a loaded disciple ({@code awal}: the first choice of a fresh recruit, a weighted draw).
	 * Returns the new path when it changed, else null.
	 */
	public static @Nullable JalanHidup periksa(DiscipleEntity murid, String sebab) {
		DiscipleData data = murid.data();
		JalanHidup sekarang = MandiriApi.jalan(data);
		if (sekarang == JalanHidup.IBLIS) {
			return null;
		}
		ServerLevel level = (ServerLevel) murid.level();
		long hari = Irama.hari(level);
		Nilai nilai = nilai(data, hari);
		CompoundTag section = MandiriApi.bagianTulis(data);
		// Event bonuses fade at every check.
		CompoundTag bonus = section.getCompoundOrEmpty(MandiriIds.K_JALAN_BONUS);
		for (String key : bonus.keySet().toArray(new String[0])) {
			double half = bonus.getDoubleOr(key, 0.0D) * 0.5D;
			if (Math.abs(half) < 0.05D) {
				bonus.remove(key);
			} else {
				bonus.putDouble(key, half);
			}
		}
		section.putLong(MandiriIds.K_JALAN_CEK, hari);
		CompoundTag skorTag = new CompoundTag();
		for (JalanHidup j : JalanHidup.values()) {
			if (j.pilihanAwal()) {
				skorTag.putDouble(j.id(), Math.round(nilai.skor()[j.ordinal()] * 100.0D) / 100.0D);
			}
		}
		section.put(MandiriIds.K_JALAN_SKOR, skorTag);

		JalanHidup pilihan;
		if (sekarang == null) {
			pilihan = undi(nilai.skor(), murid);
		} else {
			JalanHidup best = sekarang;
			for (JalanHidup j : JalanHidup.values()) {
				if (j.pilihanAwal() && nilai.skor()[j.ordinal()] > nilai.skor()[best.ordinal()]) {
					best = j;
				}
			}
			pilihan = best != sekarang && nilai.skor()[best.ordinal()] > nilai.skor()[sekarang.ordinal()] + 0.5D ? best : sekarang;
		}
		simpanAlasan(section, nilai.alasan().get(pilihan.ordinal()));
		if (pilihan == sekarang) {
			return null;
		}
		tetapkan(murid, sekarang, pilihan, sebab);
		return pilihan;
	}

	/** A fresh recruit: draw by {@code skor^2} among the nine paths (39.3.2). */
	private static JalanHidup undi(double[] skor, DiscipleEntity murid) {
		double total = 0.0D;
		for (JalanHidup j : JalanHidup.values()) {
			if (j.pilihanAwal()) {
				double s = Math.max(0.1D, skor[j.ordinal()]);
				total += s * s;
			}
		}
		double roll = murid.getRandom().nextDouble() * total;
		for (JalanHidup j : JalanHidup.values()) {
			if (!j.pilihanAwal()) {
				continue;
			}
			double s = Math.max(0.1D, skor[j.ordinal()]);
			roll -= s * s;
			if (roll <= 0.0D) {
				return j;
			}
		}
		return JalanHidup.PERTAPA;
	}

	/** Sets the path (also used by the IBLIS overlay and the test command), with every visible consequence. */
	public static void tetapkan(DiscipleEntity murid, @Nullable JalanHidup dari, JalanHidup ke, String sebab) {
		DiscipleData data = murid.data();
		CompoundTag section = MandiriApi.bagianTulis(data);
		ServerLevel level = (ServerLevel) murid.level();
		MinecraftServer server = level.getServer();
		long hari = Irama.hari(level);
		section.putString(MandiriIds.K_JALAN, ke.id());
		section.putLong(MandiriIds.K_JALAN_HARI, hari);
		SosokApi.setJalan(murid, ke.id());
		UUID owner = murid.ownerId();
		UUID soul = murid.soulId();
		MandiriBus.jalanGanti(server, owner, soul, dari, ke);
		String nama = murid.getName().getString();
		if (dari == null) {
			MandiriApi.catat(owner, soul, new EntriHarian(hari, Irama.jam(level), "jalan",
					"kultivator.sekte.mandiri.inti.harian.jalan_awal." + ke.id(), List.of(nama), EntriHarian.Suasana.TENANG, false));
			MandiriApi.kabar(server, owner, soul, "kultivator.sekte.mandiri.inti.kabar.jalan_awal", false, nama, ke.kunciNama());
			ServerPlayer master = owner == null ? null : server.getPlayerList().getPlayer(owner);
			if (master != null) {
				Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + MandiriIds.ADV_JALAN);
			}
			return;
		}
		if (ke == JalanHidup.IBLIS || dari == JalanHidup.IBLIS) {
			// The overlay and its redemption tell their own story (JalanIblis).
			return;
		}
		Ingatan.remember(server, data, "mandiri.jalan_ganti", null, 3, "kultivator.sekte.mandiri.inti.ingatan.jalan_ganti." + ke.id(),
				"");
		MandiriApi.catat(owner, soul, new EntriHarian(hari, Irama.jam(level), "jalan",
				"kultivator.sekte.mandiri.inti.harian.jalan_ganti." + ke.id(), List.of(nama), EntriHarian.Suasana.BANGGA, true));
		MandiriApi.kabar(server, owner, soul, "kultivator.sekte.mandiri.inti.kabar.jalan_ganti", true, nama, dari.kunciNama(),
				ke.kunciNama());
		MandiriApi.beritahu(server, owner, soul, Kebijakan.Pemberitahuan.JALAN_GANTI,
				Component.translatable("kultivator.sekte.mandiri.inti.pesan.jalan_ganti", murid.getName(),
						Component.translatable(dari.kunciNama()), Component.translatable(ke.kunciNama())), false);
	}

	private static void simpanAlasan(CompoundTag section, List<String> alasan) {
		ListTag list = new ListTag();
		for (String a : alasan) {
			if (list.size() < 6) {
				list.add(StringTag.valueOf(a));
			}
		}
		section.put(MandiriIds.K_JALAN_ALASAN, list);
	}

	// ================================================================== inputs from other parts

	/** The master pushed {@code jalan} (+0.8 until removed; pushing the current path again clears every push). */
	public static void dorong(DiscipleEntity murid, JalanHidup jalan) {
		if (murid == null || jalan == null || !jalan.pilihanAwal()) {
			return;
		}
		CompoundTag section = MandiriApi.bagianTulis(murid.data());
		ListTag list = new ListTag();
		if (jalan != MandiriApi.jalan(murid.data())) {
			list.add(StringTag.valueOf(jalan.id()));
		}
		section.put(MandiriIds.K_JALAN_DORONG, list);
		periksa(murid, "guru");
	}

	/** An event bonus to a path's next checks (39.3.5), e.g. {@code +0.5} pedang after a lost final. */
	public static void bonus(DiscipleData data, JalanHidup jalan, double delta) {
		CompoundTag section = MandiriApi.bagianTulis(data);
		CompoundTag bonus = section.getCompound(MandiriIds.K_JALAN_BONUS).orElse(null);
		if (bonus == null) {
			bonus = new CompoundTag();
			section.put(MandiriIds.K_JALAN_BONUS, bonus);
		}
		bonus.putDouble(jalan.id(), Math.max(-2.0D, Math.min(2.0D, bonus.getDoubleOr(jalan.id(), 0.0D) + delta)));
	}

	/** One planned hour of {@code k}: the habit of every path it belongs to grows by one hour. */
	public static void kebiasaan(DiscipleData data, Kegiatan k) {
		if (k == Kegiatan.KOSONG) {
			return;
		}
		CompoundTag section = MandiriApi.bagianTulis(data);
		CompoundTag habit = section.getCompound(MandiriIds.K_KEBIASAAN).orElse(null);
		for (JalanHidup j : JalanHidup.values()) {
			if (khas(j, k)) {
				if (habit == null) {
					habit = new CompoundTag();
					section.put(MandiriIds.K_KEBIASAAN, habit);
				}
				habit.putDouble(j.id(), habit.getDoubleOr(j.id(), 0.0D) + 1.0D);
			}
		}
	}

	/** Daily fade of the habits (x0.9: about ten days of memory). */
	public static void pudarkan(DiscipleData data, int hari) {
		CompoundTag habit = MandiriApi.bagian(data).getCompound(MandiriIds.K_KEBIASAAN).orElse(null);
		if (habit == null || hari <= 0) {
			return;
		}
		double factor = Math.pow(0.9D, Math.min(60, hari));
		CompoundTag live = MandiriApi.bagianTulis(data).getCompoundOrEmpty(MandiriIds.K_KEBIASAAN);
		for (String key : live.keySet().toArray(new String[0])) {
			double value = live.getDoubleOr(key, 0.0D) * factor;
			if (value < 0.5D) {
				live.remove(key);
			} else {
				live.putDouble(key, value);
			}
		}
	}

	/** True when the ten-day check is due. */
	public static boolean jatuhTempo(DiscipleData data, long hari) {
		return hari - MandiriApi.bagian(data).getLongOr(MandiriIds.K_JALAN_CEK, -100L) >= 10L;
	}

	private PilihJalan() {
	}
}

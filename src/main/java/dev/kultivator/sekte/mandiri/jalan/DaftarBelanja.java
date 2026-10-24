package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.talisman.TalismanKind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The shopping list (DESIGN 39.6.3), rebuilt every morning from real needs: the pills of the next breakthrough and the
 * Marrow Cleansing Pill of a low root (39B's {@code Tumbuh.butuh}, contract C39.B4), healing pills, the herbs of an
 * alchemist, the talisman paper of a formation adept, the travel talismans of adventurers and hunters, the next sword or
 * robe tier, gifts for a friend, a sweetheart or the master, and the sect's errands paid from the hall's weekly allowance.
 * At most 12 lines {@code {item, n, prio, alasan, maksHarga, ke}} in {@code extra.mandiri.belanja}; {@code maksHarga} =
 * base x (prio 1: 1.25, 2: 1.05, 3: 0.90) x (lazy 1.10, cunning 0.90, merchant 0.85). The drive {@code barang} of the
 * planner reads the list (39A {@code Dorongan.barang}), so a long list sends the disciple to town.
 */
public final class DaftarBelanja {

	public static final int MAKS = 12;
	/** Destinations of a bought line. */
	public static final String KE_TAS = "tas";
	public static final String KE_LUMBUNG = "lumbung";
	public static final String KE_GURU = "guru";
	public static final String KE_HADIAH = "hadiah:";

	/** One line of the list. */
	public record Baris(String item, int n, int prio, String alasan, long maksHarga, String ke) {

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("item", item);
			tag.putInt("n", n);
			tag.putInt("prio", prio);
			tag.putString("alasan", alasan);
			tag.putLong("maksHarga", maksHarga);
			tag.putString("ke", ke);
			return tag;
		}

		public static Baris fromTag(CompoundTag tag) {
			return new Baris(tag.getStringOr("item", ""), Math.max(1, tag.getIntOr("n", 1)), Math.max(1, Math.min(3, tag.getIntOr("prio", 3))),
					tag.getStringOr("alasan", ""), tag.getLongOr("maksHarga", 0L), tag.getStringOr("ke", KE_TAS));
		}
	}

	/** The current list (empty when none). */
	public static List<Baris> baca(DiscipleData data) {
		ListTag list = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_BELANJA);
		List<Baris> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			Baris b = Baris.fromTag(list.getCompoundOrEmpty(i));
			if (!b.item().isEmpty()) {
				out.add(b);
			}
		}
		return out;
	}

	/** Writes the list (sorted by priority, at most {@link #MAKS}). */
	public static void tulis(DiscipleData data, List<Baris> lines) {
		List<Baris> sorted = new ArrayList<>(lines);
		sorted.sort((a, b) -> Integer.compare(a.prio(), b.prio()));
		ListTag list = new ListTag();
		for (Baris b : sorted) {
			if (list.size() >= MAKS) {
				break;
			}
			list.add(b.toTag());
		}
		MandiriApi.bagianTulis(data).put(MandiriIds.K_BELANJA, list);
	}

	/** Removes {@code n} of {@code item} going to {@code ke} (bought); the line goes when it reaches 0. */
	public static void coret(DiscipleData data, String item, String ke, int n) {
		List<Baris> lines = baca(data);
		List<Baris> out = new ArrayList<>(lines.size());
		for (Baris b : lines) {
			if (n > 0 && b.item().equals(item) && b.ke().equals(ke)) {
				int left = b.n() - n;
				n = Math.max(0, -left);
				if (left > 0) {
					out.add(new Baris(b.item(), left, b.prio(), b.alasan(), b.maksHarga(), b.ke()));
				}
			} else {
				out.add(b);
			}
		}
		tulis(data, out);
	}

	/** The morning rebuild of a loaded disciple (once a world day, from 06:00). */
	static void pagi(DiscipleEntity murid, long hari) {
		CompoundTag own = murid.data().extraSection(JalanIds.EXTRA);
		if (own.getLongOr("belanjaHari", -1L) == hari) {
			return;
		}
		own.putLong("belanjaHari", hari);
		tulis(murid.data(), bangun(murid));
	}

	/** Builds the list from the disciple's needs right now. */
	public static List<Baris> bangun(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		HolderLookup.Provider reg = murid.level().registryAccess();
		HatiService hati = SekteApi.hati();
		JalanHidup jalan = MandiriApi.jalan(data);
		List<Baris> out = new ArrayList<>();
		boolean pedagang = jalan == JalanHidup.PEDAGANG;
		// 1. 39B's needs: breakthrough pills, the Marrow Cleansing Pill, heart calming, qi gathering for seclusion.
		try {
			for (dev.kultivator.sekte.mandiri.tumbuh.Tumbuh.Kebutuhan need : dev.kultivator.sekte.mandiri.tumbuh.Tumbuh.butuh(murid)) {
				String id = need.item();
				if ("kultivator:golden_core_pill".equals(id) && MandiriApi.dompet(data) < Math.round(PasarBawaan.dasar(id, 0L) * 1.2D)) {
					continue;
				}
				tambah(out, data, hati, pedagang, id, Math.max(1, need.jumlah()), need.prio(), need.sebab(), KE_TAS);
			}
		} catch (RuntimeException | LinkageError exception) {
			Kultivator.LOGGER.debug("Mandiri: 39B needs unavailable", exception);
		}
		// 2. Healing pills when fewer than two are in the bag (adventurers and hunters first; hermits do without).
		if (jalan != JalanHidup.PERTAPA && Tas.jumlah(data, reg, "kultivator:healing_pill") < 2) {
			int prio = jalan == JalanHidup.PETUALANG || jalan == JalanHidup.PEMBURU ? 1 : 2;
			tambah(out, data, hati, pedagang, "kultivator:healing_pill", 3, prio, "luka", KE_TAS);
		}
		// 3. The craft of the path.
		if (jalan == JalanHidup.ALKEMIS) {
			int band = Tas.band(data);
			int age = band <= 1 ? 60 : band <= 3 ? 100 : band <= 5 ? 200 : 300;
			int have = Tas.jumlah(data, reg, "kultivator:herb");
			if (have < 3) {
				tambah(out, data, hati, pedagang, "kultivator:herb@" + age, 3 + Math.floorMod(murid.soulId().hashCode(), 4), 2, "meramu", KE_TAS);
			}
		}
		if (jalan == JalanHidup.FORMASI && Tas.jumlah(data, reg, "kultivator:blank_talisman") < 4) {
			tambah(out, data, hati, pedagang, "kultivator:blank_talisman", 4 + Math.floorMod(murid.soulId().hashCode() >>> 3, 5), 2, "jimat", KE_TAS);
		}
		if (jalan == JalanHidup.PETUALANG || jalan == JalanHidup.PEMBURU) {
			if (!Tas.adaJimat(data, reg, TalismanKind.THOUSAND_MILE)) {
				tambah(out, data, hati, pedagang, "kultivator:thousand_mile_talisman", 1, 2, "bekal", KE_TAS);
			}
			if (!Tas.adaJimat(data, reg, TalismanKind.RETURN)) {
				tambah(out, data, hati, pedagang, "kultivator:return_talisman", 1, 2, "bekal", KE_TAS);
			}
			if (!Tas.adaJimat(data, reg, TalismanKind.SHIELD)) {
				tambah(out, data, hati, pedagang, "kultivator:shield_talisman", 2, 2, "bekal", KE_TAS);
			}
			CompoundTag own = data.extraSection(JalanIds.EXTRA);
			if (!own.getBooleanOr("kantong", false)) {
				tambah(out, data, hati, pedagang, "kultivator:storage_pouch", 1, 3, "bekal", KE_TAS);
			}
		}
		// 4. Gear: the next sword tier for blades, hunters and guards; the next robe for the proud, the ambitious, merchants.
		if (jalan == JalanHidup.PEDANG || jalan == JalanHidup.PEMBURU || jalan == JalanHidup.PENJAGA) {
			String next = Perlengkapan.pedangBerikut(data);
			if (next != null && MandiriApi.dompet(data) >= PasarBawaan.dasar(next, 0L)) {
				tambah(out, data, hati, pedagang, next, 1, 2, "senjata", KE_TAS);
			}
		}
		if (hati.has(data, Sifat.SOMBONG) || hati.has(data, Sifat.AMBISIUS) || pedagang) {
			String next = Perlengkapan.jubahBerikut(data);
			if (next != null && MandiriApi.dompet(data) >= PasarBawaan.dasar(next, 0L) + Dompet.cadangan(data)) {
				tambah(out, data, hati, pedagang, next, 1, 3, "jubah", KE_TAS);
			}
		}
		// 5. Gifts: the sweetheart first (JODOH prio 1), a close friend, the master's calming tea.
		UUID owner = murid.ownerId();
		long hariIni = murid.level() instanceof ServerLevel lv ? dev.kultivator.dunia.kota.Kota.day(lv.getServer()) : 0L;
		if (owner != null) {
			UUID kekasih = pertama(owner, murid.soulId(), Ikatan.KEKASIH);
			if (kekasih == null) {
				kekasih = pertama(owner, murid.soulId(), Ikatan.PASANGAN_DAO);
			}
			boolean jodoh = punyaCita(data, JenisCita.JODOH);
			if (kekasih != null) {
				tambah(out, data, hati, pedagang, "kultivator:hidangan/arak_bunga_persik", 1, jodoh ? 1 : 3, "hadiah", KE_HADIAH + kekasih);
			}
			UUID sahabat = pertama(owner, murid.soulId(), Ikatan.SAHABAT);
			if (sahabat != null && (hati.has(data, Sifat.PENYAYANG) || Math.floorMod(murid.soulId().hashCode() + (int) hariIni, 3) == 0)) {
				tambah(out, data, hati, pedagang, "kultivator:teh_awan_ungu", 1, 3, "hadiah", KE_HADIAH + sahabat);
			}
			if (hati.has(data, Sifat.SETIA) || hati.has(data, Sifat.PENYAYANG)) {
				tambah(out, data, hati, pedagang, "kultivator:teh_penenang_hati", 1, 3, "hadiah_guru", KE_GURU);
			}
			// 6. Sect errands (SETIA): healing pills for the Lumbung, paid from the hall's weekly allowance.
			Kebijakan kebijakan = MandiriApi.kebijakan(murid);
			if (hati.has(data, Sifat.SETIA) && kebijakan.titipanNbMinggu() > 0 && murid.level() instanceof ServerLevel level
					&& sisaTitipan(level.getServer(), owner, kebijakan) >= 12L) {
				tambah(out, data, hati, pedagang, "kultivator:healing_pill", 3, 2, "titipan", KE_LUMBUNG);
			}
		}
		return out;
	}

	private static void tambah(List<Baris> out, DiscipleData data, HatiService hati, boolean pedagang, String item, int n, int prio,
			String alasan, String ke) {
		for (Baris b : out) {
			if (b.item().equals(item) && b.ke().equals(ke)) {
				return;
			}
		}
		long base = PasarBawaan.dasar(item, 0L);
		if (base <= 0L) {
			return;
		}
		double f = prio == 1 ? 1.25D : prio == 2 ? 1.05D : 0.90D;
		if (hati.has(data, Sifat.MALAS)) {
			f *= 1.10D;
		}
		if (hati.has(data, Sifat.LICIK)) {
			f *= 0.90D;
		}
		if (pedagang) {
			f *= 0.85D;
		}
		out.add(new Baris(item, n, prio, alasan, Math.max(1L, Math.round(base * f)), ke));
	}

	private static @Nullable UUID pertama(UUID owner, UUID soul, Ikatan kind) {
		try {
			List<UUID> list = SekteApi.hubungan().with(owner, soul, kind);
			return list.isEmpty() ? null : list.get(0);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	static boolean punyaCita(DiscipleData data, JenisCita jenis) {
		ListTag cita = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_CITA);
		for (int i = 0; i < cita.size(); i++) {
			if (jenis.id().equals(cita.getCompoundOrEmpty(i).getStringOr("jenis", ""))) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== the hall's errand allowance (39.6.3 Titipan)

	/** NB of the weekly errand allowance not spent in the last 7 days. */
	static long sisaTitipan(MinecraftServer server, UUID owner, Kebijakan kebijakan) {
		CompoundTag rec = MandiriApi.aula(server, owner, MandiriIds.A_JALAN).getCompoundOrEmpty("_titipan");
		long hari = dev.kultivator.dunia.kota.Kota.day(server);
		long spent = rec.getLongOr("minggu", -1L) == Math.floorDiv(hari, 7L) ? rec.getLongOr("nb", 0L) : 0L;
		return Math.max(0L, kebijakan.titipanNbMinggu() - spent);
	}

	/** Books {@code nb} of errand purchases against the allowance. */
	static void pakaiTitipan(MinecraftServer server, UUID owner, long nb) {
		CompoundTag hall = MandiriApi.aula(server, owner, MandiriIds.A_JALAN);
		CompoundTag rec = hall.getCompound("_titipan").orElse(null);
		long week = Math.floorDiv(dev.kultivator.dunia.kota.Kota.day(server), 7L);
		if (rec == null || rec.getLongOr("minggu", -1L) != week) {
			rec = new CompoundTag();
			rec.putLong("minggu", week);
			hall.put("_titipan", rec);
		}
		rec.putLong("nb", rec.getLongOr("nb", 0L) + nb);
		MandiriApi.tandai(server);
	}

	private DaftarBelanja() {
	}
}

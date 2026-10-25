package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.power.NatalMandiri;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The disciple's own natal treasure (DESIGN 39.6.7 "Bulu ekor roh, berlian: cita PUSAKA", 20.3.17): once a world day in
 * the afternoon (14:00-18:00), a free disciple at home that carries the materials in its bag works on its life-bound
 * treasure by itself, through the bridge {@code NatalMandiri} and the old rules and chances.
 * <ul>
 *   <li><b>Refining the form</b> (Foundation or higher, inside the hall's territory): 1 diamond and 8 Spirit Tail Feathers
 *   from the bag. The form follows the path: Pendekar Pedang and Pemburu a sword, Penjaga the wok, Ahli Formasi and
 *   Cendekiawan the bell, Pertapa and Alkemis the parasol; otherwise the Dao (Fire the wok, Body the sword...).</li>
 *   <li><b>A fold of spirit enhancement</b>: n feathers from the bag and n x 40 personal PK; the old chance (Jari Penempa
 *   Roh, Fire Dao, hall level 5) and the old loss of a fold from fold 3. The policy's caution decides when a fold with a
 *   risk of loss is tried: Aman never, Wajar from a 60 % chance, Berani from 40 %, Nekat always.</li>
 * </ul>
 * Who: disciples of the sword, the hunt and the guard, any with an active PUSAKA goal, and any that already have a form.
 * The effects are the old ones ({@code FX_NATAL_REFINE}, {@code FX_NATAL_FOLD}, anvil and chimes); the diary, the hall
 * news and, at silver / gold / Eternal, a notice tell the master.
 */
final class NatalSendiri {

	/** Afternoon hours of the daily attempt. */
	static final int JAM_MULAI = 14;
	static final int JAM_AKHIR = 18;

	/** Once a second per loaded disciple (cheap until the day's check is due). */
	static void tick20(DiscipleEntity murid) {
		if (murid.role() != DiscipleEntity.Role.DISCIPLE || murid.ownerId() == null || !MandiriApi.aktif()) {
			return;
		}
		MinecraftServer server = murid.level().getServer();
		if (server == null) {
			return;
		}
		DiscipleData data = murid.data();
		long hari = DataKerja.hari(server);
		if (DataKerja.baca(data).getLongOr(DataKerja.NATAL_HARI, -1L) >= hari) {
			return;
		}
		int jam = DataKerja.jam(server);
		if (jam < JAM_MULAI || jam >= JAM_AKHIR || !bebas(murid)) {
			return;
		}
		DataKerja.tulis(data).putLong(DataKerja.NATAL_HARI, hari);
		if (!mau(murid)) {
			return;
		}
		if (NatalMandiri.form(data) < 0) {
			tempa(murid);
		} else {
			lipat(murid);
		}
	}

	/** Free at home: no order pause, not Patuh, idle or resting in the planner, not fighting, duelling or breaking through. */
	private static boolean bebas(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		if (murid.getTarget() != null || data.breakingThrough() || DuelRules.inDuel(murid) || murid.isReleasing() || murid.mode().follows()) {
			return false;
		}
		if (MandiriApi.kebijakan(murid).mode() == ModeMandiri.PATUH || MandiriApi.bebasSampai(data) > murid.level().getGameTime()) {
			return false;
		}
		Kegiatan running = MandiriApi.kegiatan(murid);
		if (running == null) {
			return murid.activity() == DiscipleEntity.ACTIVITY_NONE;
		}
		return running == Kegiatan.ISTIRAHAT || running == Kegiatan.MERENUNG || running == Kegiatan.MAKAN;
	}

	/** Who works on a natal treasure by itself (see the class comment). */
	private static boolean mau(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		if (NatalMandiri.form(data) >= 0) {
			return true;
		}
		JalanHidup jalan = MandiriApi.jalan(data);
		if (jalan == JalanHidup.PEDANG || jalan == JalanHidup.PEMBURU || jalan == JalanHidup.PENJAGA) {
			return true;
		}
		ListTag cita = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_CITA);
		for (int i = 0; i < cita.size(); i++) {
			if (JenisCita.PUSAKA.id().equals(cita.getCompoundOrEmpty(i).getStringOr("jenis", ""))) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== refining

	private static void tempa(DiscipleEntity murid) {
		Item feather = NatalMandiri.feather();
		int bulu = jumlah(murid, feather);
		int intan = jumlah(murid, Items.DIAMOND);
		if (NatalMandiri.refineBlocker(murid, intan, bulu) != null) {
			return;
		}
		List<Bahan.Ambil> a = ambilTas(murid, Items.DIAMOND, NatalMandiri.REFINE_DIAMONDS);
		List<Bahan.Ambil> b = ambilTas(murid, feather, NatalMandiri.REFINE_FEATHERS);
		if (a.isEmpty() || b.isEmpty()) {
			Bahan.kembalikan(murid, a);
			Bahan.kembalikan(murid, b);
			return;
		}
		int form = bentuk(murid.data());
		String why = NatalMandiri.refine(murid, form, NatalMandiri.REFINE_DIAMONDS, NatalMandiri.REFINE_FEATHERS);
		if (why != null) {
			Bahan.kembalikan(murid, a);
			Bahan.kembalikan(murid, b);
			return;
		}
		String nama = NatalMandiri.formKey(form);
		KabarKerja.catat(murid, "natal", "natal.tempa", EntriHarian.Suasana.BANGGA, true, nama);
		KabarKerja.kabar(murid, "natal_tempa", true, murid.getName().getString(), nama);
		KabarKerja.beritahu(murid, Kebijakan.Pemberitahuan.TEKNIK, KabarKerja.baris("pesan.natal_tempa", 0xFFD54A, KabarKerja.nama(murid),
				Component.translatable(nama)), true);
		KabarKerja.suara(murid, "cheer", 2400);
	}

	/** The form a disciple chooses by itself. */
	static int bentuk(DiscipleData data) {
		JalanHidup jalan = MandiriApi.jalan(data);
		if (jalan != null) {
			switch (jalan) {
				case PEDANG, PEMBURU -> {
					return NatalMandiri.SWORD;
				}
				case PENJAGA -> {
					return NatalMandiri.WOK;
				}
				case FORMASI, CENDEKIA -> {
					return NatalMandiri.BELL;
				}
				case PERTAPA, ALKEMIS -> {
					return NatalMandiri.PARASOL;
				}
				default -> {
				}
			}
		}
		Dao dao = data.dao();
		if (dao == Dao.FIRE || dao == Dao.BODY) {
			return dao == Dao.FIRE ? NatalMandiri.WOK : NatalMandiri.SWORD;
		}
		if (dao == Dao.LIFE || dao == Dao.ICE) {
			return NatalMandiri.PARASOL;
		}
		if (dao == Dao.SPACE || dao == Dao.SHADOW) {
			return NatalMandiri.BELL;
		}
		return NatalMandiri.SWORD;
	}

	// ================================================================== a fold

	private static void lipat(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		Item feather = NatalMandiri.feather();
		int bulu = jumlah(murid, feather);
		if (NatalMandiri.enhanceBlocker(murid, bulu) != null) {
			return;
		}
		int n = NatalMandiri.folds(data) + 1;
		double chance = NatalMandiri.chanceNext(murid);
		if (n >= 3 && !berani(MandiriApi.kebijakan(murid).risiko(), chance)) {
			return;
		}
		List<Bahan.Ambil> taken = ambilTas(murid, feather, n);
		if (taken.isEmpty()) {
			return;
		}
		int hasil = NatalMandiri.enhance(murid, n);
		if (hasil == Integer.MIN_VALUE) {
			Bahan.kembalikan(murid, taken);
			return;
		}
		String nama = NatalMandiri.formKey(NatalMandiri.form(data));
		String lipatan = String.valueOf(NatalMandiri.folds(data));
		if (hasil > 0) {
			boolean tonggak = n == NatalMandiri.SILVER || n == NatalMandiri.GOLD || n == NatalMandiri.ETERNAL;
			KabarKerja.catat(murid, "natal", "natal.lipat", EntriHarian.Suasana.BANGGA, tonggak, nama, lipatan,
					String.valueOf(Math.round(chance * 100.0D)));
			if (tonggak) {
				String inti = n == NatalMandiri.SILVER ? "natal.perak" : n == NatalMandiri.GOLD ? "natal.emas" : "natal.abadi";
				KabarKerja.catat(murid, "natal", inti, EntriHarian.Suasana.BANGGA, true, nama, lipatan);
				KabarKerja.kabar(murid, "natal_tonggak", true, murid.getName().getString(), nama, lipatan);
				KabarKerja.beritahu(murid, Kebijakan.Pemberitahuan.TEKNIK, KabarKerja.baris("pesan.natal_tonggak", 0xFFD54A,
						KabarKerja.nama(murid), Component.translatable(nama), n), true);
				KabarKerja.suara(murid, "cheer", 2400);
			}
		} else {
			KabarKerja.catat(murid, "natal", hasil < 0 ? "natal.hilang" : "natal.gagal", EntriHarian.Suasana.KESAL, false, nama,
					String.valueOf(n), lipatan);
		}
	}

	/** Whether the policy's caution tries a fold with a risk of loss at this chance. */
	static boolean berani(Kehatian risiko, double chance) {
		return switch (risiko) {
			case AMAN -> false;
			case WAJAR -> chance >= 0.60D;
			case BERANI -> chance >= 0.40D;
			case NEKAT -> true;
		};
	}

	// ================================================================== the bag only (39.6.7: "bahan dari tas")

	private static int jumlah(DiscipleEntity murid, Item item) {
		int n = 0;
		for (ItemStack stack : Bahan.isiTas(murid, stack -> stack.is(item))) {
			n += stack.getCount();
		}
		return n;
	}

	/** {@code n} of {@code item} out of the bag only (all or nothing). */
	private static List<Bahan.Ambil> ambilTas(DiscipleEntity murid, Item item, int n) {
		if (jumlah(murid, item) < n) {
			return List.of();
		}
		List<Bahan.Ambil> out = new java.util.ArrayList<>();
		int left = n;
		while (left > 0) {
			ItemStack part = MandiriApi.ambilTas(murid.data(), Bahan.registries(murid), stack -> stack.is(item), left);
			if (part.isEmpty()) {
				break;
			}
			left -= part.getCount();
			out.add(new Bahan.Ambil(part, false));
		}
		if (left > 0) {
			Bahan.kembalikan(murid, out);
			return List.of();
		}
		return out;
	}

	/** {@code uji perguruan mandiri kerja natal}: today's attempt now, whatever the hour (the day mark is cleared first). */
	static String paksa(DiscipleEntity murid) {
		CompoundTag own = DataKerja.tulis(murid.data());
		own.remove(DataKerja.NATAL_HARI);
		int formBefore = NatalMandiri.form(murid.data());
		int foldsBefore = NatalMandiri.folds(murid.data());
		if (!mau(murid)) {
			return "tidak_mau";
		}
		if (formBefore < 0) {
			tempa(murid);
		} else {
			lipat(murid);
		}
		own.putLong(DataKerja.NATAL_HARI, DataKerja.hari(murid.level().getServer()));
		return NatalMandiri.form(murid.data()) != formBefore || NatalMandiri.folds(murid.data()) != foldsBefore ? "ok" : "tidak_bisa";
	}
}

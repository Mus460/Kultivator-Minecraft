package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.PasarMurid;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The town visit (DESIGN 39.6.6): three to seven stops chosen from the shopping list and the character, each a stage
 * window of the journey's 3-8 town hours. A stop resolves as data when its window ends (buying with bargaining, a meal,
 * tea and rumours, the guild board, an auction bid, incense at the temple, the healer, selling one's own work, the night
 * market, the black market's temptation) and, while a player is near, a town body plays it ({@link Jelmaan}): it walks
 * to the stall of the stop's keeper (a 38 life point of the stop's kind, else the town NPC of the stop's role found once
 * per stop), plays the stop's clip with its prop and the keeper turns and answers.
 * <p>
 * Keepers are remembered per town and stop ({@link JalanData#pedagang}): the same pill merchant greets the disciples
 * every time, and from the third meeting on a regular customer gets 5 % off (39.8.3).
 */
final class KunjunganKota {

	/** One kind of stop: what it sells, who keeps it, where in a 38 place, how long the body stays, its clips. */
	enum Stop {
		GERBANG("gerbang", Set.of(), List.of(NpcRole.PENJAGA_GERBANG, NpcRole.PENJAGA), "", 100, GerakId.TATAP_LANGIT, PropId.TIDAK_ADA,
				0, 0, "pasar"),
		LAPAK_PIL("lapak_pil", Set.of(PasarMurid.PIL, PasarMurid.HERBA), List.of(NpcRole.PEDAGANG_PIL, NpcRole.ALKEMIS), "lapak", 600,
				GerakId.LIHAT_BARANG, PropId.BOTOL_PIL, GerakId.SERU_DAGANG, PropId.LABU_PIL, "pasar"),
		PANDAI("pandai", Set.of(PasarMurid.JIMAT, PasarMurid.SENJATA), List.of(NpcRole.PANDAI_PUSAKA, NpcRole.PEDAGANG_SENJATA, NpcRole.EMPU),
				"lapak", 500, GerakId.TEBAS_UDARA, PropId.PEDANG_KAYU, GerakId.PALU_LANDASAN, PropId.PALU, "pasar"),
		PASAR("pasar", Set.of(PasarMurid.JUBAH, PasarMurid.HADIAH, PasarMurid.TEH, PasarMurid.MAKANAN),
				List.of(NpcRole.PEDAGANG_BAZAR, NpcRole.PEDAGANG_KELILING, NpcRole.WARGA), "lapak", 500, GerakId.LIHAT_BARANG, PropId.KAIN,
				GerakId.SERU_DAGANG, PropId.KANTONG_UANG, "pasar"),
		KEDAI_TEH("kedai_teh", Set.of(), List.of(NpcRole.PEMILIK_TEH, NpcRole.AHLI_TEH), "kursi", 1200, GerakId.MINUM_TEH, PropId.CANGKIR,
				GerakId.TUANG_TEH, PropId.TEKO, "kedai_teh"),
		RUMAH_MAKAN("rumah_makan", Set.of(), List.of(NpcRole.KOKI, NpcRole.PELAYAN, NpcRole.PEMILIK_PENGINAPAN), "meja_makan", 800,
				GerakId.MAKAN, PropId.MANGKUK, GerakId.MASAK, PropId.SENDOK_SAYUR, "pasar"),
		PAPAN_MISI("papan_misi", Set.of(), List.of(NpcRole.PETUGAS_GILDA, NpcRole.KETUA_GILDA, NpcRole.PENJAGA), "obrol", 300,
				GerakId.MEMBACA, PropId.GULUNGAN, GerakId.BICARA_TANGAN, PropId.PAPAN_CATATAN, "gilda"),
		BALAI_LELANG("balai_lelang", Set.of(), List.of(NpcRole.JURU_LELANG), "kursi", 1200, GerakId.DUDUK_KURSI, PropId.KIPAS_LIPAT,
				GerakId.PALU_HAKIM, PropId.PALU_KAYU, "pasar"),
		KUIL("kuil", Set.of(), List.of(NpcRole.RAHIB, NpcRole.PEZIARAH, NpcRole.KEPALA_BIARA), "dupa", 600, GerakId.SUJUD_DUPA, PropId.DUPA,
				GerakId.SIKAP_DOA, PropId.TASBIH_TANGAN, "pasar"),
		TABIB("tabib", Set.of(), List.of(NpcRole.TABIB), "kursi", 1200, GerakId.DUDUK_KURSI, PropId.TIDAK_ADA, GerakId.PERIKSA_NADI,
				PropId.KOTAK_OBAT, "pasar"),
		LAPAK_SEKTE("lapak_sekte", Set.of(), List.of(NpcRole.PEDAGANG_BAZAR, NpcRole.WARGA), "lapak", 1200, GerakId.SERU_DAGANG,
				PropId.KANTONG_UANG, GerakId.LIHAT_BARANG, PropId.TIDAK_ADA, "pasar"),
		PASAR_MALAM("pasar_malam", Set.of(PasarMurid.HADIAH, PasarMurid.MAKANAN), List.of(NpcRole.PEDAGANG_KELILING, NpcRole.WARGA), "jalan",
				1200, GerakId.JALAN_SANTAI, PropId.LENTERA_TANGAN, GerakId.SERU_DAGANG, PropId.LENTERA_TANGAN, "pasar_malam"),
		PASAR_GELAP("pasar_gelap", Set.of(PasarMurid.PIL, PasarMurid.JIMAT), List.of(NpcRole.MAKELAR_GELAP), "", 600, GerakId.BISIK,
				PropId.KANTONG_UANG, GerakId.HITUNG_KOIN_GELAP, PropId.KANTONG_UANG, "pasar");

		final String id;
		final Set<String> jual;
		final List<NpcRole> peran;
		final String titik;
		final int tampil;
		final int klip;
		final int prop;
		final int klipPenjaga;
		final int propPenjaga;
		final String pertemuan;

		Stop(String id, Set<String> jual, List<NpcRole> peran, String titik, int tampil, int klip, int prop, int klipPenjaga, int propPenjaga,
				String pertemuan) {
			this.id = id;
			this.jual = jual;
			this.peran = peran;
			this.titik = titik;
			this.tampil = tampil;
			this.klip = klip;
			this.prop = prop;
			this.klipPenjaga = klipPenjaga;
			this.propPenjaga = propPenjaga;
			this.pertemuan = pertemuan;
		}

		static @Nullable Stop byId(String id) {
			for (Stop s : values()) {
				if (s.id.equals(id)) {
					return s;
				}
			}
			return null;
		}
	}

	/** The C39.C3 stage name of a stop ({@code kedai_teh}, {@code pasar}, {@code pasar_malam}, {@code gilda}). */
	static String jenisPertemuan(String stop) {
		Stop s = Stop.byId(stop);
		return s == null ? "pasar" : s.pertemuan;
	}

	/** The market kind of an item id. */
	static String jenisBarang(String item) {
		if (item == null) {
			return "";
		}
		if (item.startsWith("kultivator:pedang_tier_")) {
			return PasarMurid.SENJATA;
		}
		if (item.startsWith("kultivator:jubah_tier_")) {
			return PasarMurid.JUBAH;
		}
		if (item.contains(":herb")) {
			return PasarMurid.HERBA;
		}
		if (item.endsWith("_pill")) {
			return PasarMurid.PIL;
		}
		if (item.endsWith("talisman") || item.endsWith("storage_pouch")) {
			return PasarMurid.JIMAT;
		}
		if (item.contains(":teh_")) {
			return PasarMurid.TEH;
		}
		if (item.contains("hidangan/") || item.startsWith("minecraft:")) {
			return PasarMurid.HADIAH;
		}
		return PasarMurid.HADIAH;
	}

	// ================================================================== the plan of the visit

	/** The stop stages of the visit from {@code t}, arriving at world hour {@code jamTiba}. */
	static List<CompoundTag> susun(ServerLevel level, Rencana r, long t, int jamTiba, RandomSource rnd) {
		Set<String> perlu = new java.util.HashSet<>();
		boolean prio1 = false;
		for (UUID soul : r.rombongan()) {
			ListTag list = r.anggota(soul).getListOrEmpty("daftar");
			for (int i = 0; i < list.size(); i++) {
				DaftarBelanja.Baris b = DaftarBelanja.Baris.fromTag(list.getCompoundOrEmpty(i));
				perlu.add(jenisBarang(b.item()));
				prio1 |= b.prio() == 1;
			}
		}
		CompoundTag lead = r.anggota(r.soul());
		int sifat = lead.getIntOr("sifat", 0);
		JalanHidup jalan = Watak.jalan(lead);
		EnumSet<Stop> stops = EnumSet.noneOf(Stop.class);
		stops.add(Stop.GERBANG);
		if (perlu.contains(PasarMurid.PIL) || perlu.contains(PasarMurid.HERBA)) {
			stops.add(Stop.LAPAK_PIL);
		}
		if (perlu.contains(PasarMurid.JIMAT) || perlu.contains(PasarMurid.SENJATA)) {
			stops.add(Stop.PANDAI);
		}
		if (perlu.contains(PasarMurid.JUBAH) || perlu.contains(PasarMurid.HADIAH) || perlu.contains(PasarMurid.TEH)) {
			stops.add(Stop.PASAR);
		}
		if (r.jenis() == JenisJalan.MISI_GILDA) {
			stops.add(Stop.PAPAN_MISI);
		}
		if (r.jenis() == JenisJalan.JUAL) {
			stops.add(Stop.LAPAK_SEKTE);
		}
		boolean capital = r.town() != 0L && Towns.isKotaBesar(level.getServer(), r.town());
		if (jamTiba >= 14 || Watak.punya(sifat, Sifat.PENYAYANG) || rnd.nextFloat() < 0.5F) {
			stops.add(Stop.KEDAI_TEH);
		}
		if (jamTiba >= 10 && jamTiba <= 13 || jamTiba >= 16 && jamTiba <= 20 || rnd.nextFloat() < 0.6F) {
			stops.add(Stop.RUMAH_MAKAN);
		}
		if ((jalan == JalanHidup.PEDAGANG || Watak.punya(sifat, Sifat.AMBISIUS)) && r.sisaUang(r.soul()) >= 300L && jamTiba >= 15 && jamTiba <= 20) {
			stops.add(Stop.BALAI_LELANG);
		}
		if (lead.getIntOr("iblis", 0) > 30) {
			stops.add(Stop.KUIL);
		}
		if (capital && jamTiba >= 16) {
			stops.add(Stop.PASAR_MALAM);
		}
		boolean tergoda = Watak.punya(sifat, Sifat.LICIK) || Watak.punya(sifat, Sifat.PENDENDAM) || lead.getIntOr("iblis", 0) >= 40;
		if (prio1 && tergoda && rnd.nextFloat() < 0.30F && !MandiriApi.kebijakanAula(r.owner()).dilarang("pasar_gelap")) {
			stops.add(Stop.PASAR_GELAP);
		}
		List<Stop> order = new ArrayList<>(stops);
		while (order.size() > 7) {
			order.remove(order.size() - 1);
		}
		long townTicks = (3L + rnd.nextInt(6)) * Tahap.JAM;
		long share = Math.max(300L, townTicks / Math.max(1, order.size()));
		List<CompoundTag> out = new ArrayList<>(order.size());
		BlockPos at = r.tujuan().pos();
		for (Stop s : order) {
			long len = Math.max(s.tampil + 200L, s == Stop.GERBANG ? 300L : share);
			CompoundTag stage = Tahap.stage(Tahap.KUNJUNGAN, t, t + len, at, 0);
			stage.putString("stop", s.id);
			out.add(stage);
			t += len;
		}
		return out;
	}

	// ================================================================== resolving a stop

	static void selesaikan(Tahap.Konteks k, String stopId) {
		Stop stop = Stop.byId(stopId);
		if (stop == null) {
			return;
		}
		Rencana r = k.r();
		JalanData data = JalanData.get(k.server());
		long town = r.town() != 0L ? r.town() : r.tujuan().pos().asLong();
		List<String> kota = k.kota();
		CompoundTag keeper = data.pedagang(town, stop.id, JalanIds.orang((int) (town ^ stop.ordinal() * 0x51ED27L), stop.ordinal() % 2 == 1));
		List<String> nama = List.of(keeper.getStringOr("a", ""), keeper.getStringOr("b", ""));
		int jumpa = data.jumpaPedagang(town, stop.id);
		List<String> base = new ArrayList<>();
		base.add(k.namaPemimpin());
		base.addAll(kota);
		base.addAll(nama);
		switch (stop) {
			case GERBANG -> {
				r.kisah(JalanIds.k("kisah.kota.gerbang." + k.rnd().nextInt(4)), base);
				r.kenangan("mandiri.kota");
			}
			case LAPAK_PIL, PANDAI, PASAR, PASAR_GELAP -> belanja(k, stop, town, base, jumpa);
			case KEDAI_TEH -> teh(k, town, base);
			case RUMAH_MAKAN -> makan(k, base);
			case PAPAN_MISI -> r.kisah(JalanIds.k("kisah.kota.papan_misi"), base);
			case BALAI_LELANG -> lelang(k, base);
			case KUIL -> {
				long dupa = MandiriConfig.get().harga.dupa;
				for (UUID soul : r.rombongan()) {
					if (r.sisaUang(soul) >= dupa) {
						r.ubahUang(soul, -dupa);
						Tahap.tambahIblis(r, soul, -3);
					}
				}
				r.kisah(JalanIds.k("kisah.kota.kuil." + k.rnd().nextInt(3)), base);
				Tahap.catat(k, r.soul(), "kota.kuil", JalanIds.k("harian.kota.kuil"), base, EntriHarian.Suasana.TENANG, false);
			}
			case TABIB -> tabib(k, base);
			case LAPAK_SEKTE -> jual(k, base);
			case PASAR_MALAM -> {
				for (UUID soul : r.rombongan()) {
					if (r.sisaUang(soul) >= 3L) {
						r.ubahUang(soul, -2L);
						Tahap.tambahAngka(r, "honor", soul, 1);
					}
				}
				belanja(k, stop, town, base, jumpa);
				r.kisah(JalanIds.k("kisah.kota.pasar_malam." + k.rnd().nextInt(3)), base);
				Tahap.catat(k, r.soul(), "kota.pasar_malam", JalanIds.k("harian.kota.pasar_malam"), base, EntriHarian.Suasana.SENANG, false);
			}
		}
		if (stop != Stop.GERBANG) {
			MasalahKota.coba(k, stop, town, base);
		}
	}

	/** Units of {@code item} for {@code ke} already bought by {@code soul} on this journey. */
	static int sudahDibeli(Rencana r, UUID soul, String item, String ke) {
		int n = 0;
		ListTag list = r.daftarBarang();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			if (soul.toString().equals(one.getStringOr("untuk", "")) && item.equals(one.getStringOr("item", ""))
					&& ke.equals(one.getStringOr("ke", "")) && one.getBooleanOr("beli", false)) {
				n += one.getIntOr("n", 0);
			}
		}
		return n;
	}

	/** Bag slots still free for {@code soul} after what is planned to go into the bag. */
	static int ruangSisa(Rencana r, UUID soul) {
		int used = 0;
		ListTag list = r.daftarBarang();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			if (soul.toString().equals(one.getStringOr("untuk", "")) && DaftarBelanja.KE_TAS.equals(one.getStringOr("ke", ""))
					&& !Perlengkapan.semu(one.getStringOr("item", ""))) {
				used++;
			}
		}
		return Math.max(0, r.anggota(soul).getIntOr("ruang", 0) - used);
	}

	private static void belanja(Tahap.Konteks k, Stop stop, long town, List<String> base, int jumpa) {
		Rencana r = k.r();
		PasarMurid pasar = MandiriApi.pasar();
		boolean gelap = stop == Stop.PASAR_GELAP;
		if (!gelap && KontrakKota.tokoTutup(k.server(), town)) {
			r.kisah(JalanIds.k("kisah.kota.tutup"), base);
			return;
		}
		boolean bought = false;
		for (UUID soul : r.rombongan()) {
			CompoundTag who = r.anggota(soul);
			int sifat = who.getIntOr("sifat", 0);
			ListTag list = who.getListOrEmpty("daftar");
			for (int i = 0; i < list.size(); i++) {
				DaftarBelanja.Baris b = DaftarBelanja.Baris.fromTag(list.getCompoundOrEmpty(i));
				String kind = jenisBarang(b.item());
				if (!stop.jual.contains(kind) || gelap && b.prio() != 1) {
					continue;
				}
				int want = b.n() - sudahDibeli(r, soul, b.item(), b.ke());
				if (want <= 0) {
					continue;
				}
				long asked = gelap ? Math.round(PasarBawaan.dasar(b.item(), k.r().benih()) * 1.5D) : pasar.harga(town, b.item(), k.hari());
				if (asked <= 0L) {
					continue;
				}
				if (jumpa >= 3 && !gelap) {
					asked = Math.max(1L, Math.round(asked * 0.95D));
				}
				Tawar.Hasil deal = Tawar.tawar(k.server(), sifat, Watak.jalan(who), r.owner(), town, asked, k.rnd());
				if (deal.tersinggung()) {
					List<String> args = new ArrayList<>(base);
					args.add(JalanIds.kunciBarang(b.item()));
					r.kisah(JalanIds.k("kisah.kota.tersinggung"), args);
					Tahap.catat(k, soul, "kota.tawar", JalanIds.k("harian.kota.tersinggung"), args, EntriHarian.Suasana.KESAL, false);
					break;
				}
				long price = deal.akhir();
				if (!gelap && price > b.maksHarga()) {
					List<String> args = new ArrayList<>(base);
					args.add(JalanIds.kunciBarang(b.item()));
					args.add(String.valueOf(price));
					r.kisah(JalanIds.k("kisah.kota.mahal"), args);
					continue;
				}
				long reserve = b.prio() == 1 ? 0L : 10L * (who.getIntOr("band", 0) + 1L);
				boolean errand = DaftarBelanja.KE_LUMBUNG.equals(b.ke());
				long budget = errand ? DaftarBelanja.sisaTitipan(k.server(), r.owner(), MandiriApi.kebijakanAula(r.owner())) : r.sisaUang(soul) - reserve;
				int n = (int) Math.min(want, budget / Math.max(1L, price));
				if (DaftarBelanja.KE_TAS.equals(b.ke()) && !Perlengkapan.semu(b.item()) && ruangSisa(r, soul) <= 0) {
					n = 0;
				}
				if (n <= 0) {
					continue;
				}
				if (!gelap && !pasar.pesan(town, b.item(), n, k.hari())) {
					List<String> args = new ArrayList<>(base);
					args.add(JalanIds.kunciBarang(b.item()));
					r.kisah(JalanIds.k("kisah.kota.habis"), args);
					continue;
				}
				long total = price * n;
				if (errand) {
					DaftarBelanja.pakaiTitipan(k.server(), r.owner(), total);
				} else {
					r.ubahUang(soul, -total);
				}
				r.barang(soul, b.item(), n, b.ke(), total, true);
				MandiriBus.belanja(k.server(), r.owner(), soul, town, b.item(), n, total);
				bought = true;
				List<String> args = new ArrayList<>(base);
				args.add(JalanIds.kunciBarang(b.item()));
				args.add(String.valueOf(n));
				args.add(String.valueOf(asked));
				args.add(String.valueOf(price));
				String gaya = asked == price ? "pas" : deal.gaya();
				r.kisah(JalanIds.k("kisah.kota.beli." + gaya), args);
				Tahap.catat(k, soul, "belanja", JalanIds.k("harian.belanja." + gaya), args,
						price < asked ? EntriHarian.Suasana.SENANG : EntriHarian.Suasana.TENANG, b.prio() == 1);
				if (gelap) {
					// temptation +4 and the black-market day are written to the tablet at home (Kepulangan)
					Tahap.tambahIblis(r, soul, 4);
					Rencana.child(r.hasil(), "gelap").putLong(soul.toString(), k.hari());
				}
			}
		}
		if (gelap) {
			MasalahKota.razia(k, town, base);
		} else if (!bought && stop != Stop.PASAR_MALAM) {
			r.kisah(JalanIds.k("kisah.kota.lihat_saja." + k.rnd().nextInt(3)), base);
		}
	}

	private static void teh(Tahap.Konteks k, long town, List<String> base) {
		Rencana r = k.r();
		for (UUID soul : r.rombongan()) {
			if (r.sisaUang(soul) >= 1L) {
				r.ubahUang(soul, -1L);
			}
		}
		String kabar = "kabar." + k.rnd().nextInt(12);
		List<String> args = new ArrayList<>(base);
		args.add(JalanIds.k(kabar));
		r.kisah(JalanIds.k("kisah.kota.teh"), args);
		r.hasil().putString("kabar", kabar);
		Tahap.catat(k, r.soul(), "kota.teh", JalanIds.k("harian.kota.teh"), args, EntriHarian.Suasana.TENANG, false);
	}

	private static void makan(Tahap.Konteks k, List<String> base) {
		Rencana r = k.r();
		int[] dishes = MandiriConfig.get().harga.hidangan;
		int pick = 0;
		for (UUID soul : r.rombongan()) {
			long have = r.sisaUang(soul);
			int choice = have >= dishes[2] * 4L ? 2 : have >= dishes[1] * 3L ? 1 : 0;
			if (Watak.punya(r.anggota(soul).getIntOr("sifat", 0), Sifat.SOMBONG) && have >= dishes[3] * 2L) {
				choice = 3;
			}
			if (have >= dishes[choice]) {
				r.ubahUang(soul, -dishes[choice]);
			}
			Tahap.tambahAngka(r, "lelah", soul, -15);
			if (soul.equals(r.soul())) {
				pick = choice;
			}
		}
		List<String> args = new ArrayList<>(base);
		args.add(JalanIds.k("hidangan." + pick));
		args.add(String.valueOf(dishes[pick]));
		r.kisah(JalanIds.k("kisah.kota.makan"), args);
		Tahap.catat(k, r.soul(), "kota.makan", JalanIds.k("harian.kota.makan." + pick), args, EntriHarian.Suasana.SENANG, false);
	}

	private static void lelang(Tahap.Konteks k, List<String> base) {
		Rencana r = k.r();
		UUID lead = r.soul();
		long purse = r.sisaUang(lead);
		String[] lots = {"kultivator:heavenvoid_fragment", "kultivator:lost_scripture_page", "kultivator:jade_slip", "kultivator:storage_ring_low"};
		String lot = lots[k.rnd().nextInt(lots.length)];
		long dasar = PasarBawaan.dasar(lot, 0L);
		long bid = Math.round(dasar * (1.2D + k.rnd().nextDouble() * 0.3D));
		List<String> args = new ArrayList<>(base);
		args.add(JalanIds.kunciBarang(lot));
		args.add(String.valueOf(bid));
		long reserve = 10L * (r.anggota(lead).getIntOr("band", 0) + 1L);
		if (dasar > 0L && purse - reserve >= bid && ruangSisa(r, lead) > 0 && k.rnd().nextFloat() < 0.30F) {
			r.ubahUang(lead, -bid);
			r.barang(lead, lot, 1, DaftarBelanja.KE_TAS, bid, true);
			r.kisah(JalanIds.k("kisah.kota.lelang.menang"), args);
			Tahap.catat(k, lead, "kota.lelang", JalanIds.k("harian.kota.lelang.menang"), args, EntriHarian.Suasana.BANGGA, true);
		} else {
			r.kisah(JalanIds.k("kisah.kota.lelang.kalah"), args);
			Tahap.catat(k, lead, "kota.lelang", JalanIds.k("harian.kota.lelang.kalah"), args, EntriHarian.Suasana.KESAL, false);
		}
	}

	private static void tabib(Tahap.Konteks k, List<String> base) {
		Rencana r = k.r();
		boolean healed = false;
		for (UUID soul : r.rombongan()) {
			if (r.luka(soul) == JenisLuka.BERAT || r.luka(soul) == JenisLuka.TERLUKA) {
				long fee = (long) MandiriConfig.get().harga.tabibPerBand * (r.anggota(soul).getIntOr("band", 0) + 1L);
				if (r.luka(soul) == JenisLuka.TERLUKA) {
					fee = Math.max(1L, fee / 3L);
				}
				if (r.sisaUang(soul) >= fee) {
					r.ubahUang(soul, -fee);
					r.sembuhkan(soul);
					healed = true;
					List<String> args = new ArrayList<>(base);
					args.add(String.valueOf(fee));
					Tahap.catat(k, soul, "kota.tabib", JalanIds.k("harian.kota.tabib"), args, EntriHarian.Suasana.TENANG, false);
				}
			}
		}
		r.kisah(JalanIds.k(healed ? "kisah.kota.tabib.sembuh" : "kisah.kota.tabib.periksa"), base);
	}

	/**
	 * The disciple's own work at the Sect Stall or the market (39.6.8): the pieces 39D offered at the gate
	 * ({@code MandiriApi.dagangan}, marked {@code buatan_murid}, never bought goods) sell at 0.35-0.40 x their town value;
	 * the money goes through the earning rules at home, the pieces leave the bag there.
	 */
	private static void jual(Tahap.Konteks k, List<String> base) {
		Rencana r = k.r();
		UUID lead = r.soul();
		ListTag works = r.anggota(lead).getListOrEmpty("dagangan");
		long earned = 0L;
		int sold = 0;
		for (int i = 0; i < works.size() && sold < 4; i++) {
			CompoundTag one = works.getCompoundOrEmpty(i);
			long value = one.getLongOr("nilai", 0L);
			int n = one.getIntOr("n", 0);
			if (value <= 0L || n <= 0) {
				continue;
			}
			long price = Math.max(1L, Math.round(value * n * (0.35D + k.rnd().nextDouble() * 0.05D)));
			earned += price;
			sold++;
			r.barang(lead, one.getStringOr("item", ""), n, "jual", price, false);
		}
		List<String> args = new ArrayList<>(base);
		args.add(String.valueOf(sold));
		args.add(String.valueOf(earned));
		if (earned > 0L) {
			r.tambahPendapatan(lead, earned);
			r.kisah(JalanIds.k("kisah.kota.jual.laku"), args);
			Tahap.catat(k, lead, "kota.jual", JalanIds.k("harian.kota.jual"), args, EntriHarian.Suasana.BANGGA, false);
		} else {
			r.kisah(JalanIds.k("kisah.kota.jual.sepi"), args);
		}
	}

	/** The overnight stay: the town inn 4 NB (a road post 2 NB), a night of rest. */
	static void menginap(Tahap.Konteks k) {
		Rencana r = k.r();
		boolean post = r.town() == 0L;
		long fee = post ? MandiriConfig.get().harga.penginapanPos : MandiriConfig.get().harga.penginapan;
		for (UUID soul : r.rombongan()) {
			if (r.sisaUang(soul) >= fee) {
				r.ubahUang(soul, -fee);
			}
			Tahap.tambahAngka(r, "lelah", soul, -40);
		}
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		args.addAll(k.kota());
		args.add(String.valueOf(fee));
		r.kisah(JalanIds.k(post ? "kisah.menginap.pos" : "kisah.menginap.kota"), args);
	}

	private KunjunganKota() {
	}
}

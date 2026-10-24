package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The branching escalation ladder of a feud (DESIGN 39.F.3). Heat 0-100 rises with incidents and steps and falls by
 * {@code panasTurun} a day (half as fast while the sect's member holds a grudge, not at all for ten days after a vow or
 * while the master has forbidden it). Each time the heat crosses the next threshold the next rung happens and its branch
 * is rolled from the design's base chances, multiplied by the traits of both sides, the sects' stance, the master's
 * actions and the elders:
 * <pre>
 * T0 Tegang 1 · T1 Cekcok 15 · T2 Duel Kehormatan 30 · T3 Tawuran 45 · T4 Sergapan 60 · T5 Campur tangan 70
 * T6 Insiden diplomatik 80 · T7 Pemicu besar 90
 * </pre>
 * The intensity setting cuts the ladder (RENDAH T3, SEDANG T6, TINGGI T7); a sect bound to the hall by a pact never goes
 * past T3 (its elders part them). Endings are DAMAI, SAINGAN seumur hidup and DENDAM; each writes lasting memories, the
 * bus event {@code SETERU_AKHIR} and the diary. No one dies on any rung (36.0.3, {@code bolehGugur} false).
 */
final class Tangga {

	// ================================================================== beginning a feud

	/**
	 * Starts a feud of {@code murid} with {@code tokoh}'s sect for trigger {@code sebab} (or heats the disciple's running
	 * feud with that sect). Null when the hall is full ({@code seteruMaksAula}), the disciple already feuds with another
	 * sect, or the sect is a vassal of the hall.
	 */
	static @Nullable Perseteruan mulai(MinecraftServer server, UUID owner, MuridInfo murid, TokohSekte tokoh, String sebab, String salah,
			int panasAwal, long town, BlockPos pos) {
		SeteruConfig config = SeteruConfig.get();
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		if (DiplomasiMandiri.bawahan(server, owner, tokoh.sekte)) {
			return null;
		}
		Perseteruan own = aula.milik(murid.soul());
		if (own != null) {
			if (!own.sekte.equals(tokoh.sekte)) {
				return null;
			}
			own.tambahTokoh(tokoh.id);
			panas(server, own, panasAwal, "riwayat.panas_lagi", Teks.arg(murid.nama()), tokoh.argNama());
			return own;
		}
		Perseteruan withSect = aula.dengan(tokoh.sekte);
		if (withSect != null && withSect.jiwa.size() < Perseteruan.JIWA_MAKS) {
			// one feud per sect: a second disciple joins it
			withSect.tambahJiwa(murid.soul(), murid.nama());
			withSect.tambahTokoh(tokoh.id);
			panas(server, withSect, panasAwal / 2, "riwayat.ikut", Teks.arg(murid.nama()), tokoh.argNama());
			return withSect;
		}
		if (aula.aktif().size() >= config.seteruMaksAula) {
			return null;
		}
		long today = Ingatan.today(server);
		Perseteruan f = new Perseteruan(data.idBaru(), owner, tokoh.sekte, sebab);
		f.tambahJiwa(murid.soul(), murid.nama());
		f.tambahTokoh(tokoh.id);
		f.salah = salah;
		f.hariMulai = today;
		f.hariTurun = today;
		f.pos = pos.asLong();
		f.situs = town;
		f.tempat = Teks.argKota(town);
		// an old grudge burns hotter: a member who already hates this disciple adds 10
		int start = panasAwal + (tokoh.benci(murid.soul()) ? 10 : 0);
		aula.seteru.add(f);
		aula.rapikan();
		f.catat(today, "riwayat.mulai." + sebab, Teks.arg(murid.nama()), tokoh.argNama(), Teks.argSekte(tokoh.sekte), f.tempat);
		data.setDirty();
		MandiriBus.seteruMulai(server, owner, f.id, f.sekte, sebab);
		SekteLog.log(server, owner, MandiriIds.MODUL, Teks.t("log.mulai", murid.nama(), tokoh.namaTampil(), Teks.sekte(tokoh.sekte),
				Teks.t("sebab." + sebab)));
		harian(server, owner, murid.soul(), "seteru.mulai", "harian.mulai." + sebab, EntriHarian.Suasana.KESAL, false,
				tokoh.argNama(), Teks.argSekte(tokoh.sekte), f.tempat);
		tokoh.ubahSikap(murid.soul(), -10);
		if ((SeteruIds.SEBAB_PERUNDUNG.equals(sebab) || SeteruIds.SEBAB_DESA.equals(sebab)) && SeteruIds.SALAH_MEREKA.equals(salah)) {
			Kabar.sebar(server, f, SeteruIds.KABAR_PEMBELA);
		} else if (SeteruIds.SEBAB_GULUNGAN.equals(sebab)) {
			Kabar.sebar(server, f, SeteruIds.KABAR_GULUNGAN);
		}
		panas(server, f, start, "riwayat.panas_awal");
		return f;
	}

	// ================================================================== heat

	/** Adds {@code delta} heat (a frozen feud takes no more), then climbs every rung the new heat reaches. */
	static void panas(MinecraftServer server, Perseteruan f, int delta, String kunci, String... arg) {
		if (!f.aktif() || delta == 0 || SeteruIds.TERLARANG.equals(f.status)) {
			return;
		}
		if (delta > 0 && f.beku) {
			return;
		}
		long today = Ingatan.today(server);
		int before = f.panas;
		f.panas = Math.max(0, Math.min(100, f.panas + delta));
		if (kunci != null && !kunci.isEmpty() && f.panas != before) {
			String[] all = new String[arg.length + 1];
			System.arraycopy(arg, 0, all, 0, arg.length);
			all[arg.length] = (delta > 0 ? "+" : "") + (f.panas - before);
			f.catat(today, kunci, all);
		}
		SeteruData.get(server).setDirty();
		naikBila(server, f, null);
	}

	/** Climbs while the heat stands above the next threshold and the cap allows. */
	private static void naikBila(MinecraftServer server, Perseteruan f, @Nullable String cabang) {
		int guard = 0;
		while (f.aktif() && guard++ < 8) {
			int next = f.tingkat + 1;
			if (next > SeteruIds.T_BESAR || f.panas < SeteruIds.AMBANG[next]) {
				return;
			}
			int cap = batas(server, f);
			if (next > cap) {
				tahan(server, f, cap);
				return;
			}
			langkah(server, f, next, cabang);
			cabang = null;
		}
	}

	/** Forces the next rung now ({@code uji ... naik <id> [cabang]}). */
	static void naik(MinecraftServer server, Perseteruan f, @Nullable String cabang) {
		int next = Math.min(SeteruIds.T_BESAR, f.tingkat + 1);
		if (f.panas < SeteruIds.AMBANG[next]) {
			f.panas = SeteruIds.AMBANG[next];
		}
		langkah(server, f, next, cabang);
		naikBila(server, f, null);
	}

	/** The highest rung this feud may reach: intensity, pacts (T3), and the module's own rules. */
	static int batas(MinecraftServer server, Perseteruan f) {
		int cap = SeteruConfig.get().tingkatIntensitas().maks();
		if (DiplomasiMandiri.berpakta(server, f.owner, f.sekte)) {
			cap = Math.min(cap, SeteruIds.T_TAWURAN);
		}
		return cap;
	}

	/** The heat stops below the next rung; a pact's elders part them with their line (39.F.8 test 8). */
	private static void tahan(MinecraftServer server, Perseteruan f, int cap) {
		long today = Ingatan.today(server);
		int top = cap + 1 <= SeteruIds.T_BESAR ? SeteruIds.AMBANG[cap + 1] - 1 : 100;
		f.panas = Math.min(f.panas, top);
		if (cap == SeteruIds.T_TAWURAN && DiplomasiMandiri.berpakta(server, f.owner, f.sekte) && f.bolehAksi(100, today)) {
			f.aksiHari.putLong("100", today);
			f.panas = Math.max(0, SeteruIds.AMBANG[SeteruIds.T_TAWURAN] - 1);
			f.catat(today, "riwayat.lerai", Teks.argSekte(f.sekte));
			Adegan.lerai(server, f);
			beritahu(server, f, Teks.t("pesan.lerai", f.namaUtama(), Teks.sekte(f.sekte)), false, false);
		}
		SeteruData.get(server).setDirty();
	}

	// ================================================================== the rungs

	static RandomSource acak(Perseteruan f, long salt) {
		return RandomSource.create(f.id.hashCode() * 0x9E3779B97F4A7C15L ^ f.owner.getLeastSignificantBits() ^ salt * 0xBF58476D1CE4E5B9L);
	}

	private static void langkah(MinecraftServer server, Perseteruan f, int t, @Nullable String cabang) {
		long today = Ingatan.today(server);
		f.tingkat = Math.max(f.tingkat, t);
		RandomSource rnd = acak(f, today * 17L + t);
		SeteruData data = SeteruData.get(server);
		UUID soul = f.jiwaUtama();
		MuridInfo murid = soul == null ? null : MuridInfo.dari(f.owner, soul);
		TokohSekte tokoh = data.tokoh(f.tokohUtama());
		MandiriBus.seteruNaik(server, f.owner, f.id, t);
		SekteLog.log(server, f.owner, MandiriIds.MODUL, Teks.t("log.naik", f.namaUtama(), Teks.sekte(f.sekte), Teks.t("tingkat." + t)));
		switch (t) {
			case SeteruIds.T_TEGANG -> {
				String pick = cabang != null ? cabang : rnd.nextDouble() < 0.60D ? "lewat" : "naik";
				f.catat(today, "riwayat.t0." + pick, Teks.arg(f.namaUtama()), tokoh == null ? "" : tokoh.argNama());
				Adegan.gelembung(server, f, "hmph");
				if ("naik".equals(pick)) {
					f.panas = Math.min(100, f.panas + 5);
				} else {
					f.panas = Math.max(0, f.panas - 3);
				}
			}
			case SeteruIds.T_CEKCOK -> cekcok(server, f, murid, tokoh, rnd, cabang, today);
			case SeteruIds.T_DUEL -> {
				f.catat(today, "riwayat.t2", Teks.arg(f.namaUtama()), tokoh == null ? "" : tokoh.argNama());
				DuelSeteru.jadwalkan(server, f);
				tanya(server, f, t);
			}
			case SeteruIds.T_TAWURAN -> {
				f.catat(today, "riwayat.t3", Teks.argSekte(f.sekte));
				Tawuran.jadwalkan(server, f);
				tanya(server, f, t);
			}
			case SeteruIds.T_SERGAPAN -> {
				f.catat(today, "riwayat.t4", Teks.argSekte(f.sekte));
				Patroli.buat(server, f);
				tanya(server, f, t);
			}
			case SeteruIds.T_CAMPUR -> campur(server, f, murid, tokoh, rnd, cabang, today);
			case SeteruIds.T_DIPLOMATIK -> diplomatik(server, f, rnd, today);
			case SeteruIds.T_BESAR -> besar(server, f, rnd, cabang, today);
			default -> {
			}
		}
		data.setDirty();
	}

	/** T1: a quarrel; apology 25 % (humble x2) / a challenge 45 % / leaving with a grudge 30 %. */
	private static void cekcok(MinecraftServer server, Perseteruan f, @Nullable MuridInfo murid, @Nullable TokohSekte tokoh, RandomSource rnd,
			@Nullable String cabang, long today) {
		Adegan.cekcok(server, f);
		double maaf = 0.25D;
		if (murid != null && murid.punya(Sifat.RENDAH_HATI) || tokoh != null && tokoh.punya(Sifat.RENDAH_HATI)) {
			maaf *= 2.0D;
		}
		double tantang = 0.45D;
		if (murid != null && (murid.punya(Sifat.SOMBONG) || murid.punya(Sifat.BERANI)) || tokoh != null && tokoh.punya(Sifat.SOMBONG)) {
			tantang *= 1.3D;
		}
		double pergi = 0.30D;
		String pick = cabang != null ? cabang : pilih(rnd, new String[]{"maaf", "tantang", "pergi"}, new double[]{maaf, tantang, pergi});
		String nama = Teks.arg(f.namaUtama());
		String them = tokoh == null ? "" : tokoh.argNama();
		f.catat(today, "riwayat.t1." + pick, nama, them);
		switch (pick) {
			case "maaf" -> {
				f.panas = Math.max(0, f.panas - 20);
				if (tokoh != null && f.jiwaUtama() != null) {
					tokoh.ubahSikap(f.jiwaUtama(), 15);
				}
				if (f.panas <= 10) {
					akhiri(server, f, SeteruIds.DAMAI, "akhir.maaf");
				}
			}
			case "tantang" -> f.panas = Math.max(f.panas, SeteruIds.AMBANG[SeteruIds.T_DUEL]);
			default -> {
				f.panas = Math.min(100, f.panas + 4);
				if (tokoh != null && f.jiwaUtama() != null && tokoh.punya(Sifat.PENDENDAM)) {
					tokoh.tambahDendam(f.jiwaUtama());
				}
				if (f.jiwaUtama() != null) {
					Antrean.ingat(server, f.owner, f.jiwaUtama(), SeteruIds.ING_DIHINA, null, -2, SeteruIds.k("ingat.dihina"), them);
				}
			}
		}
	}

	/** T5: the elders meet: apology (the guilty side, 40 %), compensation (30 %), a vow of revenge (30 %). */
	private static void campur(MinecraftServer server, Perseteruan f, @Nullable MuridInfo murid, @Nullable TokohSekte tokoh, RandomSource rnd,
			@Nullable String cabang, long today) {
		double sumpah = 0.30D;
		if (tokoh != null && tokoh.punya(Sifat.PENDENDAM) || murid != null && murid.punya(Sifat.PENDENDAM)) {
			sumpah *= 1.5D;
		}
		String pick = cabang != null ? cabang : pilih(rnd, new String[]{"maaf", "ganti_rugi", "sumpah"}, new double[]{0.40D, 0.30D, sumpah});
		f.catat(today, "riwayat.t5." + pick, Teks.argSekte(f.sekte));
		switch (pick) {
			case "maaf" -> {
				Adegan.maaf(server, f);
				damaiSebagian(server, f, 40, "maaf");
			}
			case "ganti_rugi" -> {
				Adegan.gantiRugi(server, f);
				if (f.korban()) {
					bayarKorban(server, f, 10 * Math.max(1, SekteApi.diplomasi().tier(f.sekte)), "seteru");
				}
				damaiSebagian(server, f, 40, "ganti_rugi");
			}
			default -> {
				f.sumpahSampai = today + 10L;
				Adegan.sumpah(server, f);
				for (UUID soul : f.jiwa) {
					Antrean.ingat(server, f.owner, soul, SeteruIds.ING_SUMPAH, null, -5, SeteruIds.k("ingat.sumpah"), Teks.argSekte(f.sekte));
				}
				beritahu(server, f, Teks.t("pesan.sumpah", f.namaUtama(), Teks.sekte(f.sekte)), true, true);
				Kabar.sebar(server, f, SeteruIds.KABAR_SUMPAH);
			}
		}
	}

	/** Apology or compensation: heat -40, standing +3. */
	static void damaiSebagian(MinecraftServer server, Perseteruan f, int turun, String sebab) {
		f.panas = Math.max(0, f.panas - turun);
		DiplomasiMandiri.insiden(server, f.owner, f.sekte, 3, sebab);
		if (f.panas < SeteruIds.AMBANG[SeteruIds.T_CEKCOK]) {
			akhiri(server, f, SeteruIds.DAMAI, "akhir." + sebab);
		}
	}

	/** T6: the master of their sect hears of it: standing -5..-15 (at most {@code standingMingguMaks} a week). */
	private static void diplomatik(MinecraftServer server, Perseteruan f, RandomSource rnd, long today) {
		int want = 5 + rnd.nextInt(11);
		int allowed = sisaMinggu(server, f, today);
		int delta = Math.min(want, allowed);
		if (delta <= 0) {
			f.catat(today, "riwayat.t6_batas", Teks.argSekte(f.sekte));
			return;
		}
		if (DiplomasiMandiri.insiden(server, f.owner, f.sekte, -delta, f.sebab)) {
			catatMinggu(server, f, today, delta);
			f.catat(today, "riwayat.t6", Teks.argSekte(f.sekte), "n:" + delta);
			DiplomasiMandiri.kronik(server, f.owner, f.sekte, Teks.t("kronik.insiden", f.namaUtama(), Teks.t("sebab." + f.sebab)));
			beritahu(server, f, Teks.t("pesan.insiden", Teks.sekte(f.sekte), delta), true, true);
		}
	}

	private static int sisaMinggu(MinecraftServer server, Perseteruan f, long today) {
		SeteruData.Aula aula = SeteruData.get(server).aula(f.owner);
		var week = aula.standingMinggu.getCompoundOrEmpty(f.sekte);
		long minggu = today / 7L;
		int used = week.getLongOr("minggu", -1L) == minggu ? week.getIntOr("jumlah", 0) : 0;
		return Math.max(0, SeteruConfig.get().standingMingguMaks - used);
	}

	private static void catatMinggu(MinecraftServer server, Perseteruan f, long today, int delta) {
		SeteruData.Aula aula = SeteruData.get(server).aula(f.owner);
		var week = aula.standingMinggu.getCompoundOrEmpty(f.sekte).copy();
		long minggu = today / 7L;
		int used = week.getLongOr("minggu", -1L) == minggu ? week.getIntOr("jumlah", 0) : 0;
		week.putLong("minggu", minggu);
		week.putInt("jumlah", used + delta);
		aula.standingMinggu.put(f.sekte, week);
	}

	/** T7: the sect master acts through D (official challenge 60 %, war 25 % only when allowed, envoy 15 %). */
	private static void besar(MinecraftServer server, Perseteruan f, RandomSource rnd, @Nullable String cabang, long today) {
		double roll = switch (cabang == null ? "" : cabang) {
			case "tantang_resmi" -> 0.10D;
			case "perang" -> 0.70D;
			case "utusan" -> 0.95D;
			default -> rnd.nextDouble();
		};
		DiplomasiMandiri.Langkah step = DiplomasiMandiri.pemicuBesar(server, f.owner, f.sekte, roll, SeteruConfig.get().perangBoleh());
		switch (step) {
			case TANTANG -> {
				f.resmi = true;
				f.catat(today, "riwayat.t7.tantang", Teks.argSekte(f.sekte));
				beritahu(server, f, Teks.t("pesan.tantang_resmi", Teks.sekte(f.sekte), f.namaUtama()), true, false);
				akhiri(server, f, SeteruIds.SAINGAN, "akhir.resmi");
			}
			case PERANG -> {
				f.catat(today, "riwayat.t7.perang", Teks.argSekte(f.sekte));
				beritahu(server, f, Teks.t("pesan.perang", Teks.sekte(f.sekte)), true, false);
			}
			case UTUSAN, DAMAI -> {
				f.catat(today, "riwayat.t7.utusan", Teks.argSekte(f.sekte));
				f.panas = Math.max(0, f.panas - 30);
				beritahu(server, f, Teks.t("pesan.utusan", Teks.sekte(f.sekte)), true, false);
			}
			default -> f.catat(today, "riwayat.t7.tanpa_d", Teks.argSekte(f.sekte));
		}
	}

	// ================================================================== results of the scenes

	/**
	 * The honour duel ended (live or as data). The loser takes it gracefully (DAMAI or SAINGAN, 50 % when not proud or
	 * vengeful) or bitterly (heat +15, x1.5 vengeful); a cunning member of theirs who cheated (10 %) sends it to the brawl.
	 */
	static void hasilDuel(MinecraftServer server, Perseteruan f, boolean kitaMenang, boolean hidup) {
		if (!f.aktif()) {
			return;
		}
		long today = Ingatan.today(server);
		SeteruData data = SeteruData.get(server);
		TokohSekte tokoh = data.tokoh(f.tokohUtama());
		UUID soul = f.jiwaUtama();
		MuridInfo murid = soul == null ? null : MuridInfo.dari(f.owner, soul);
		RandomSource rnd = acak(f, today * 31L + 7L);
		String them = tokoh == null ? "" : tokoh.argNama();
		String nama = Teks.arg(f.namaUtama());
		f.catat(today, kitaMenang ? "riwayat.duel.menang" : "riwayat.duel.kalah", nama, them);
		if (soul != null) {
			if (kitaMenang) {
				Antrean.ingat(server, f.owner, soul, SeteruIds.ING_DUEL_MENANG, null, 5, SeteruIds.k("ingat.duel_menang"), them);
				SekteBus.honored(server, f.owner, soul, "mandiri.seteru.duel", 3);
				harian(server, f.owner, soul, "seteru.duel", "harian.duel.menang", EntriHarian.Suasana.BANGGA, true, them, Teks.argSekte(f.sekte));
			} else {
				Antrean.ingat(server, f.owner, soul, SeteruIds.ING_DUEL_KALAH, null, -3, SeteruIds.k("ingat.duel_kalah"), them);
				harian(server, f.owner, soul, "seteru.duel", "harian.duel.kalah", EntriHarian.Suasana.SEDIH, true, them, Teks.argSekte(f.sekte));
				lukai(server, f, soul, 2);
				if (murid != null && murid.punya(Sifat.PENDENDAM)) {
					MandiriApi.dendam(server, f.owner, soul, "seteru:" + (tokoh == null ? f.sekte : tokoh.id));
				}
			}
		}
		if (tokoh != null) {
			if (kitaMenang) {
				tokoh.lukaSampai = today + 2L;
				if (soul != null) {
					tokoh.ubahSikap(soul, tokoh.punya(Sifat.PENDENDAM) ? -15 : 5);
				}
			} else if (tokoh.julukan < 0 && rnd.nextInt(3) == 0) {
				tokoh.julukan = rnd.nextInt(TokohSekte.JULUKAN);
			}
		}
		prestasi(server, f.owner, MandiriIds.ADV_SETERU_DUEL);
		// the loser's heart decides what comes next
		boolean loserProud;
		boolean loserVengeful;
		if (kitaMenang) {
			loserProud = tokoh != null && tokoh.punya(Sifat.SOMBONG);
			loserVengeful = tokoh != null && tokoh.punya(Sifat.PENDENDAM);
		} else {
			loserProud = murid != null && murid.punya(Sifat.SOMBONG);
			loserVengeful = murid != null && murid.punya(Sifat.PENDENDAM);
		}
		boolean cheat = !kitaMenang && tokoh != null && tokoh.punya(Sifat.LICIK) && rnd.nextDouble() < 0.10D;
		if (cheat) {
			f.catat(today, "riwayat.duel.curang", them);
			f.panas = Math.max(f.panas, SeteruIds.AMBANG[SeteruIds.T_TAWURAN]);
			naikBila(server, f, null);
			return;
		}
		if (!loserProud && !loserVengeful && rnd.nextDouble() < 0.50D) {
			boolean rival = murid != null && (murid.punya(Sifat.AMBISIUS) || murid.punya(Sifat.BERANI))
					|| tokoh != null && (tokoh.punya(Sifat.AMBISIUS) || tokoh.punya(Sifat.BERANI));
			akhiri(server, f, rival ? SeteruIds.SAINGAN : SeteruIds.DAMAI, "akhir.lapang");
			return;
		}
		int add = loserVengeful ? 22 : 15;
		panas(server, f, add, "riwayat.duel.sakit", kitaMenang ? them : nama);
		if (!kitaMenang && soul != null && loserVengeful) {
			Antrean.kegiatan(server, f.owner, soul, dev.kultivator.sekte.mandiri.api.Kegiatan.SUMPAH, 200, today + 2L);
		}
		data.setDirty();
	}

	/** The brawl ended: they retreat (a nickname, a lesson) or ours come home hurt; heat +10 either way, elders 40 %. */
	static void hasilTawuran(MinecraftServer server, Perseteruan f, boolean kitaMenang) {
		if (!f.aktif()) {
			return;
		}
		long today = Ingatan.today(server);
		SeteruData data = SeteruData.get(server);
		TokohSekte tokoh = data.tokoh(f.tokohUtama());
		RandomSource rnd = acak(f, today * 37L + 3L);
		f.catat(today, kitaMenang ? "riwayat.tawuran.menang" : "riwayat.tawuran.kalah", Teks.argSekte(f.sekte));
		for (UUID soul : f.jiwa) {
			if (kitaMenang) {
				Antrean.ingat(server, f.owner, soul, SeteruIds.ING_TAWURAN_MENANG, null, 4, SeteruIds.k("ingat.tawuran_menang"),
						Teks.argSekte(f.sekte));
				SekteBus.honored(server, f.owner, soul, "mandiri.seteru.tawuran", 2);
			} else {
				Antrean.ingat(server, f.owner, soul, SeteruIds.ING_TAWURAN_KALAH, null, -4, SeteruIds.k("ingat.tawuran_kalah"),
						Teks.argSekte(f.sekte));
				lukai(server, f, soul, rnd.nextInt(3) == 0 ? 3 : 2);
			}
		}
		if (kitaMenang && tokoh != null) {
			tokoh.lukaSampai = today + 3L;
			if (tokoh.julukan < 0) {
				tokoh.julukan = rnd.nextInt(TokohSekte.JULUKAN);
			}
		}
		panas(server, f, 10, "riwayat.tawuran.panas");
		if (f.aktif() && f.tingkat < SeteruIds.T_CAMPUR && rnd.nextDouble() < 0.40D && batas(server, f) >= SeteruIds.T_CAMPUR) {
			f.panas = Math.max(f.panas, SeteruIds.AMBANG[SeteruIds.T_CAMPUR]);
			f.tingkat = Math.max(f.tingkat, SeteruIds.T_SERGAPAN);
			naikBila(server, f, null);
		}
		data.setDirty();
	}

	/** Our disciple was beaten: it limps home bandaged for {@code hari} days (the activity waits for its body). */
	static void lukai(MinecraftServer server, Perseteruan f, UUID soul, int hari) {
		long today = Ingatan.today(server);
		Antrean.kegiatan(server, f.owner, soul, dev.kultivator.sekte.mandiri.api.Kegiatan.TERLUKA, hari * 24000, today + hari);
	}

	/** Compensation NB into the victim's purse (when its body is loaded now, else when it is next seen). */
	static void bayarKorban(MinecraftServer server, Perseteruan f, long nb, String sebab) {
		UUID soul = f.jiwaUtama();
		if (soul == null || nb <= 0L) {
			return;
		}
		Antrean.dompet(server, f.owner, soul, nb, sebab);
	}

	// ================================================================== endings

	/** Ends the feud as {@code status} (DAMAI, SAINGAN, DENDAM, TERLARANG) with the history key {@code kunci}. */
	static void akhiri(MinecraftServer server, Perseteruan f, String status, String kunci) {
		if (!f.aktif()) {
			return;
		}
		long today = Ingatan.today(server);
		SeteruData data = SeteruData.get(server);
		f.status = status;
		f.hariAkhir = today;
		f.adegan = new net.minecraft.nbt.CompoundTag();
		f.catat(today, kunci, Teks.argSekte(f.sekte));
		List<TokohSekte> them = new ArrayList<>();
		for (String id : f.tokoh) {
			TokohSekte t = data.tokoh(id);
			if (t != null) {
				them.add(t);
			}
		}
		String themName = them.isEmpty() ? "" : them.get(0).argNama();
		for (UUID soul : f.jiwa) {
			switch (status) {
				case SeteruIds.DAMAI -> {
					Antrean.ingat(server, f.owner, soul, SeteruIds.ING_DAMAI, null, 6, SeteruIds.k("ingat.damai"), themName);
					harian(server, f.owner, soul, "seteru.akhir", "harian.akhir.damai", EntriHarian.Suasana.TENANG, true, themName,
							Teks.argSekte(f.sekte));
					for (TokohSekte t : them) {
						t.ubahSikap(soul, 25);
						t.hapusDendam(soul);
					}
				}
				case SeteruIds.SAINGAN -> {
					Antrean.ingat(server, f.owner, soul, SeteruIds.ING_SAINGAN, null, 7, SeteruIds.k("ingat.saingan"), themName);
					harian(server, f.owner, soul, "seteru.akhir", "harian.akhir.saingan", EntriHarian.Suasana.BANGGA, true, themName,
							Teks.argSekte(f.sekte));
					for (TokohSekte t : them) {
						t.ubahSikap(soul, 10);
						t.hapusDendam(soul);
					}
				}
				case SeteruIds.DENDAM -> {
					Antrean.ingat(server, f.owner, soul, SeteruIds.ING_DENDAM, null, -8, SeteruIds.k("ingat.dendam"), themName);
					harian(server, f.owner, soul, "seteru.akhir", "harian.akhir.dendam", EntriHarian.Suasana.KESAL, true, themName,
							Teks.argSekte(f.sekte));
					MandiriApi.dendam(server, f.owner, soul, "seteru:" + (them.isEmpty() ? f.sekte : them.get(0).id));
					for (TokohSekte t : them) {
						t.ubahSikap(soul, -30);
						t.tambahDendam(soul);
					}
				}
				default -> {
				}
			}
		}
		if (SeteruIds.DAMAI.equals(status)) {
			prestasi(server, f.owner, MandiriIds.ADV_SETERU_DAMAI);
			Adegan.damai(server, f);
			AsmaraLintas.cekSahabat(server, f);
			Kabar.sebar(server, f, SeteruIds.KABAR_DAMAI);
		} else if (SeteruIds.SAINGAN.equals(status)) {
			Adegan.saingan(server, f);
			Kabar.sebar(server, f, SeteruIds.KABAR_SAINGAN);
		} else if (SeteruIds.DENDAM.equals(status)) {
			Adegan.dendam(server, f);
		}
		Patroli.hapusUntuk(server, f);
		MandiriBus.seteruAkhir(server, f.owner, f.id, status);
		SekteLog.log(server, f.owner, MandiriIds.MODUL, Teks.t("log.akhir", f.namaUtama(), Teks.sekte(f.sekte), Teks.t("status." + status)));
		beritahu(server, f, Teks.t("pesan.akhir." + status.toLowerCase(java.util.Locale.ROOT), f.namaUtama(), Teks.sekte(f.sekte)), true, false);
		data.aula(f.owner).rapikan();
		data.setDirty();
	}

	// ================================================================== the daily step of a hall

	/** Once per world day per hall: heat decay, the elders' return, the endings of cooled feuds, neglect, the policy. */
	static void hariBaru(MinecraftServer server, UUID owner, SeteruData.Aula aula, long today) {
		if (aula.hari == today) {
			return;
		}
		aula.hari = today;
		SeteruConfig config = SeteruConfig.get();
		SeteruData data = SeteruData.get(server);
		Kebijakan policy = MandiriApi.kebijakanAula(owner);
		for (Perseteruan f : new ArrayList<>(aula.aktif())) {
			if (f.tetua != null && server.overworld().getGameTime() >= f.tetuaPulang + 24_000L) {
				CampurTangan.tetuaPulang(server, f);
			}
			if (!f.aktif() || SeteruIds.TERLARANG.equals(f.status)) {
				continue;
			}
			if (f.hariTurun < today && !f.beku && !f.sumpah(today)) {
				long days = f.hariTurun < 0 ? 1L : Math.min(30L, today - f.hariTurun);
				TokohSekte tokoh = data.tokoh(f.tokohUtama());
				boolean grudge = tokoh != null && f.jiwaUtama() != null && tokoh.benci(f.jiwaUtama());
				int drop = (int) Math.round(config.panasTurun * days * (grudge ? 0.5D : 1.0D));
				f.panas = Math.max(0, f.panas - drop);
			}
			f.hariTurun = today;
			if (f.panas <= 0) {
				String end = f.sumpahSampai > 0L ? SeteruIds.DENDAM : f.tingkat >= SeteruIds.T_DUEL ? SeteruIds.SAINGAN : SeteruIds.DAMAI;
				akhiri(server, f, end, "akhir.reda");
				continue;
			}
			if (f.tingkat >= SeteruIds.T_SERGAPAN && f.korban() && !f.diurus && !f.diabaikan && f.jiwaUtama() != null) {
				f.diabaikan = true;
				SekteBus.mistreated(server, owner, f.jiwaUtama(), Perlakuan.DIABAIKAN, "mandiri.seteru");
				f.catat(today, "riwayat.diabaikan", Teks.arg(f.namaUtama()));
			}
			if (policy.seteru() == Kebijakan.SikapSeteru.SELALU_DAMAIKAN && f.tingkat >= SeteruIds.T_DUEL
					&& f.bolehAksi(SeteruIds.AKSI_DAMAIKAN, today)) {
				CampurTangan.jalankan(server, null, f, SeteruIds.AKSI_DAMAIKAN);
			}
		}
		Patroli.hariBaru(server, owner, aula, today);
		data.setDirty();
	}

	// ================================================================== telling the master

	/** The stance of the hall with the feud's sect. */
	static Sikap sikap(MinecraftServer server, Perseteruan f) {
		return SekteApi.diplomasi().stance(f.owner, f.sekte);
	}

	static @Nullable ServerPlayer master(MinecraftServer server, Perseteruan f) {
		return server.getPlayerList().getPlayer(f.owner);
	}

	/** Grants advancement {@code sekte/<adv>} to the hall's master when online. */
	static void prestasi(MinecraftServer server, UUID owner, String adv) {
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + adv);
		}
	}

	/** The policy "Tanya saya" (default): at T2 and above the master gets the quick actions in chat. */
	private static void tanya(MinecraftServer server, Perseteruan f, int t) {
		Kebijakan policy = MandiriApi.kebijakanAula(f.owner);
		if (policy.seteru() == Kebijakan.SikapSeteru.BIARKAN) {
			beritahu(server, f, Teks.t("pesan.naik", f.namaUtama(), Teks.sekte(f.sekte), Teks.t("tingkat." + t)), false, false);
			return;
		}
		MutableComponent line = Teks.t("pesan.naik", f.namaUtama(), Teks.sekte(f.sekte), Teks.t("tingkat." + t));
		if (policy.seteru() == Kebijakan.SikapSeteru.TANYA) {
			line.append(" ").append(tombol("tombol.damaikan", "aksi " + f.id + " damaikan", 0x7FD8A0));
			if (t == SeteruIds.T_DUEL) {
				line.append(" ").append(tombol("tombol.dukung", "aksi " + f.id + " dukung", 0xF2C66A));
			}
			line.append(" ").append(tombol("tombol.biarkan", "aksi " + f.id + " biarkan", 0xA0A0A0));
		}
		beritahu(server, f, line, t >= SeteruIds.T_TAWURAN, true);
	}

	/** A notice of kind SETERU through 39E (or a plain chat line), with the [Lihat seteru] button. */
	static void beritahu(MinecraftServer server, Perseteruan f, Component pesan, boolean penting, boolean tombolLihat) {
		MutableComponent line = pesan.copy();
		if (tombolLihat) {
			line.append(" ").append(tombol("tombol.lihat", "", 0x8FD0FF));
		}
		MandiriApi.beritahu(server, f.owner, f.jiwaUtama(), Kebijakan.Pemberitahuan.SETERU, line, penting);
	}

	/** {@code [label]} running {@code /kultivasi perguruan mandiri seteru <rest>}. */
	static MutableComponent tombol(String labelKey, String rest, int color) {
		String command = "/kultivasi perguruan mandiri seteru" + (rest.isEmpty() ? "" : " " + rest);
		return Component.literal("[").append(Teks.t(labelKey)).append("]").withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Teks.t(labelKey + ".hover"))));
	}

	/** A diary entry of the disciple (39E composes it in the reader's language). */
	static void harian(MinecraftServer server, UUID owner, UUID soul, String jenis, String kunci, EntriHarian.Suasana suasana,
			boolean penting, String... arg) {
		long day = Ingatan.today(server);
		int jam = (int) Math.floorMod(Math.floorMod(server.overworld().getOverworldClockTime(), 24000L) / 1000L + 6L, 24L);
		MandiriApi.catat(owner, soul, new EntriHarian(day, jam, jenis, SeteruIds.k(kunci), List.of(arg), suasana, penting));
	}

	/** A weighted pick of a branch. */
	static String pilih(RandomSource rnd, String[] names, double[] weights) {
		double total = 0.0D;
		for (double w : weights) {
			total += Math.max(0.0D, w);
		}
		double roll = rnd.nextDouble() * total;
		for (int i = 0; i < names.length; i++) {
			roll -= Math.max(0.0D, weights[i]);
			if (roll < 0.0D) {
				return names[i];
			}
		}
		return names[names.length - 1];
	}

	/** Chat colour of a tier (screen and notices). */
	static ChatFormatting warna(int t) {
		return t >= SeteruIds.T_DIPLOMATIK ? ChatFormatting.DARK_RED : t >= SeteruIds.T_TAWURAN ? ChatFormatting.RED
				: t >= SeteruIds.T_CEKCOK ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
	}

	private Tangga() {
	}
}

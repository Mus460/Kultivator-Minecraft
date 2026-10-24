package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * The master's hand in a feud (DESIGN 39.F.4), from the Perseteruan screen or the chat buttons, each once a day per feud:
 * <ul>
 * <li><b>Damaikan</b>: a letter; {@code 0.5 + standing/200 + 0.1 (master's realm above their master's)}: heat -40, standing
 * +3; failed: heat -10. A vengeful victim of ours feels it unjust.</li>
 * <li><b>Utus tetua</b>: a free elder leaves for a day ({@code Halls.storeAway}, reason {@code mandiri_seteru});
 * {@code 0.55 + 0.1 (our elder's realm - theirs)}: apology or compensation; failed: home humiliated, heat +10.</li>
 * <li><b>Hukum murid sendiri</b>: heat -25, standing +3; the disciple is punished (DIHUKUM, and unjust when it was the
 * victim), a bitter memory.</li>
 * <li><b>Tuntut ganti rugi</b> (they were wrong): {@code 0.4 + 0.1 (hall tier - sect tier)}: {@code 20 x tier} NB to the
 * victim's purse, heat -30; failed: standing -5, heat +15.</li>
 * <li><b>Dukung dalam duel</b>: the master becomes the duel's challenger, the disciple fights braver.</li>
 * <li><b>Tantang tetua mereka</b> (T5+, the master at their gate or in a town): a duel with their Young Elder or Eldest
 * Senior (10 %, at most 3 minutes): won, they apologise; lost, heat +10, standing -5.</li>
 * <li><b>Larang</b>: the master forbids the matter, the heat freezes (again to lift it).</li>
 * <li><b>Biarkan</b>: the disciples settle it themselves.</li>
 * </ul>
 */
final class CampurTangan {

	/**
	 * Runs action {@code aksi} on {@code f}. {@code master} null is the hall policy "Selalu damaikan" acting alone.
	 * Returns why it could not run, or null; the master gets the outcome in chat.
	 */
	static @Nullable Component jalankan(MinecraftServer server, @Nullable ServerPlayer master, Perseteruan f, int aksi) {
		long today = Ingatan.today(server);
		if (!f.aktif()) {
			return Teks.t("alasan.selesai");
		}
		if (!f.bolehAksi(aksi, today)) {
			return Teks.t("alasan.sudah_hari_ini");
		}
		Component why = switch (aksi) {
			case SeteruIds.AKSI_DAMAIKAN -> damaikan(server, master, f, today);
			case SeteruIds.AKSI_UTUS_TETUA -> utusTetua(server, f, today);
			case SeteruIds.AKSI_HUKUM -> hukum(server, f, today);
			case SeteruIds.AKSI_TUNTUT -> tuntut(server, f, today);
			case SeteruIds.AKSI_DUKUNG -> master == null ? Teks.t("alasan.guru") : dukung(server, master, f);
			case SeteruIds.AKSI_TANTANG_TETUA -> master == null ? Teks.t("alasan.guru") : tantangTetua(server, master, f);
			case SeteruIds.AKSI_LARANG -> larang(server, f, today);
			case SeteruIds.AKSI_BIARKAN -> biarkan(server, f, today);
			case SeteruIds.AKSI_RESTUI -> master == null ? Teks.t("alasan.guru") : AsmaraLintas.restui(server, master, f);
			case SeteruIds.AKSI_PISAHKAN -> AsmaraLintas.pisahkan(server, f);
			default -> Teks.t("alasan.tidak_dikenal");
		};
		if (why == null) {
			f.pakaiAksi(aksi, today);
			if (aksi == SeteruIds.AKSI_BIARKAN) {
				f.diurus = false;
			}
		}
		SeteruData.get(server).setDirty();
		return why;
	}

	private static void kabari(MinecraftServer server, Perseteruan f, Component line) {
		ServerPlayer master = server.getPlayerList().getPlayer(f.owner);
		if (master != null) {
			Msg.chat(master, line);
		}
	}

	// ================================================================== the actions

	private static @Nullable Component damaikan(MinecraftServer server, @Nullable ServerPlayer master, Perseteruan f, long today) {
		int standing = SekteApi.diplomasi().standing(f.owner, f.sekte);
		int theirMaster = Rank.ofStep(DiplomasiMandiri.langkahKetua(server, f.sekte)).realm().ordinal();
		int mine = master == null ? -1 : Cultivators.get(master).realm().ordinal();
		double chance = 0.5D + standing / 200.0D + (mine > theirMaster ? 0.1D : 0.0D);
		chance = Math.max(0.05D, Math.min(0.95D, chance));
		RandomSource rnd = Tangga.acak(f, today * 101L + 1L);
		boolean ok = rnd.nextDouble() < chance;
		f.catat(today, ok ? "riwayat.aksi.damaikan.ok" : "riwayat.aksi.damaikan.gagal", Teks.argSekte(f.sekte), "n:" + Math.round(chance * 100.0D));
		UUID victim = f.korban() ? f.jiwaUtama() : null;
		if (victim != null && MuridInfo.dari(f.owner, victim).punya(Sifat.PENDENDAM)) {
			SekteBus.mistreated(server, f.owner, victim, Perlakuan.TIDAK_ADIL, "mandiri.seteru.damaikan");
		}
		kabari(server, f, Teks.t(ok ? "pesan.damaikan.ok" : "pesan.damaikan.gagal", Teks.sekte(f.sekte), Math.round(chance * 100.0D)));
		if (ok) {
			Tangga.damaiSebagian(server, f, 40, "damaikan");
		} else {
			Tangga.panas(server, f, -10, "riwayat.panas_damaikan");
		}
		return null;
	}

	private static @Nullable Component utusTetua(MinecraftServer server, Perseteruan f, long today) {
		if (f.tetua != null) {
			return Teks.t("alasan.tetua_pergi");
		}
		DiscipleEntity elder = null;
		Map<Jabatan, UUID> offices = SekteApi.tetua().offices(f.owner);
		for (UUID soul : offices.values()) {
			DiscipleEntity body = Antrean.loaded(f.owner, soul);
			if (body != null && !f.melibatkan(soul) && !dev.kultivator.util.SibukMurid.busy(body)) {
				elder = body;
				break;
			}
		}
		if (elder == null) {
			return Teks.t("alasan.tanpa_tetua_bebas");
		}
		long back = server.overworld().getGameTime() + 24_000L;
		CompoundTag info = new CompoundTag();
		info.putString("seteru", f.id);
		info.putString("sekte", f.sekte);
		f.tetua = elder.soulId();
		f.tetuaPulang = back;
		f.catat(today, "riwayat.aksi.utus", Teks.arg(elder.getName()), Teks.argSekte(f.sekte));
		Component name = elder.getName().copy();
		Halls.storeAway(f.owner, elder, back, MandiriIds.AWAY_SETERU, info);
		kabari(server, f, Teks.t("pesan.utus", name, Teks.sekte(f.sekte)));
		return null;
	}

	/** The elder is back (the away store's return, or the daily fallback): apology/compensation, or humiliation. */
	static void tetuaPulang(MinecraftServer server, Perseteruan f) {
		UUID elder = f.tetua;
		if (elder == null) {
			return;
		}
		f.tetua = null;
		long today = Ingatan.today(server);
		MuridInfo info = MuridInfo.dari(f.owner, elder);
		TokohSekte theirs = Roster.tetua(server, f.sekte);
		int ourRealm = info.ranah();
		int theirRealm = theirs == null ? ourRealm : theirs.rank(today, SekteApi.diplomasi().tier(f.sekte)).realm().ordinal();
		double chance = Math.max(0.10D, Math.min(0.95D, 0.55D + 0.1D * (ourRealm - theirRealm)));
		boolean ok = Tangga.acak(f, today * 211L).nextDouble() < chance;
		if (!f.aktif()) {
			return;
		}
		if (ok) {
			f.catat(today, "riwayat.aksi.utus.ok", Teks.arg(info.nama()), Teks.argSekte(f.sekte));
			for (UUID soul : f.jiwa) {
				SekteBus.honored(server, f.owner, soul, "mandiri.seteru.dibela", 2);
				Antrean.ingat(server, f.owner, soul, SeteruIds.ING_DIBELA, elder, 3, SeteruIds.k("ingat.dibela"), Teks.arg(info.nama()));
			}
			if (f.korban()) {
				Tangga.bayarKorban(server, f, 10L * Math.max(1, SekteApi.diplomasi().tier(f.sekte)), "seteru");
			}
			kabari(server, f, Teks.t("pesan.utus.ok", info.nama(), Teks.sekte(f.sekte)));
			Tangga.damaiSebagian(server, f, 40, "utusan");
		} else {
			f.catat(today, "riwayat.aksi.utus.gagal", Teks.arg(info.nama()), Teks.argSekte(f.sekte));
			Antrean.ingat(server, f.owner, elder, "mandiri.seteru.dihina_tetua", null, -3, SeteruIds.k("ingat.tetua_dihina"), Teks.argSekte(f.sekte));
			kabari(server, f, Teks.t("pesan.utus.gagal", info.nama(), Teks.sekte(f.sekte)));
			Tangga.panas(server, f, 10, "riwayat.panas_utus");
		}
	}

	private static @Nullable Component hukum(MinecraftServer server, Perseteruan f, long today) {
		for (UUID soul : f.jiwa) {
			SekteBus.mistreated(server, f.owner, soul, Perlakuan.DIHUKUM, "mandiri.seteru.hukum");
			if (f.korban()) {
				SekteBus.mistreated(server, f.owner, soul, Perlakuan.TIDAK_ADIL, "mandiri.seteru.hukum");
			}
			Antrean.ingat(server, f.owner, soul, SeteruIds.ING_DIHUKUM, f.owner, -4, SeteruIds.k("ingat.dihukum"), Teks.argSekte(f.sekte));
		}
		f.catat(today, "riwayat.aksi.hukum", Teks.arg(f.namaUtama()));
		kabari(server, f, Teks.t("pesan.hukum", f.namaUtama(), Teks.sekte(f.sekte)));
		DiplomasiMandiri.insiden(server, f.owner, f.sekte, 3, "hukum");
		Tangga.panas(server, f, -25, "riwayat.panas_hukum");
		if (f.aktif() && f.panas < SeteruIds.AMBANG[SeteruIds.T_CEKCOK]) {
			Tangga.akhiri(server, f, SeteruIds.DAMAI, "akhir.hukum");
		}
		return null;
	}

	private static @Nullable Component tuntut(MinecraftServer server, Perseteruan f, long today) {
		if (!f.korban()) {
			return Teks.t("alasan.bukan_korban");
		}
		int sectTier = Math.max(1, SekteApi.diplomasi().tier(f.sekte));
		int hallTier = Math.max(1, Halls.level(f.owner));
		double chance = Math.max(0.05D, Math.min(0.95D, 0.4D + 0.1D * (hallTier - sectTier)));
		boolean ok = Tangga.acak(f, today * 307L).nextDouble() < chance;
		if (ok) {
			long nb = 20L * sectTier;
			Tangga.bayarKorban(server, f, nb, "seteru");
			for (UUID soul : f.jiwa) {
				SekteBus.honored(server, f.owner, soul, "mandiri.seteru.tuntut", 3);
			}
			f.catat(today, "riwayat.aksi.tuntut.ok", Teks.argSekte(f.sekte), "n:" + nb);
			kabari(server, f, Teks.t("pesan.tuntut.ok", Teks.sekte(f.sekte), nb, f.namaUtama()));
			Tangga.panas(server, f, -30, "riwayat.panas_tuntut");
		} else {
			f.catat(today, "riwayat.aksi.tuntut.gagal", Teks.argSekte(f.sekte));
			kabari(server, f, Teks.t("pesan.tuntut.gagal", Teks.sekte(f.sekte)));
			DiplomasiMandiri.insiden(server, f.owner, f.sekte, -5, "tuntut");
			Tangga.panas(server, f, 15, "riwayat.panas_tuntut_gagal");
		}
		return null;
	}

	private static @Nullable Component dukung(MinecraftServer server, ServerPlayer master, Perseteruan f) {
		Component why = DuelSeteru.dukung(server, f, master);
		if (why != null) {
			return why;
		}
		for (UUID soul : f.jiwa) {
			SekteBus.honored(server, f.owner, soul, "mandiri.seteru.dukung", 4);
		}
		f.catat(Ingatan.today(server), "riwayat.aksi.dukung", Teks.arg(f.namaUtama()));
		Msg.chat(master, Teks.t("pesan.dukung", f.namaUtama()));
		return null;
	}

	private static @Nullable Component tantangTetua(MinecraftServer server, ServerPlayer master, Perseteruan f) {
		if (f.tingkat < SeteruIds.T_CAMPUR) {
			return Teks.t("alasan.belum_t5");
		}
		ServerLevel level = master.level();
		BlockPos gate = SekteApi.diplomasi().gate(f.sekte);
		if (gate == null) {
			gate = SekteApi.diplomasi().plannedGate(f.sekte);
		}
		boolean atGate = gate != null && Math.hypot(master.getX() - gate.getX(), master.getZ() - gate.getZ()) <= 600.0D;
		boolean inTown = Towns.within(level, master.blockPosition()) != 0L;
		if (!atGate && !inTown) {
			return Teks.t("alasan.bukan_gerbang");
		}
		return DuelSeteru.tantangTetua(server, f, master);
	}

	/** The master's duel with their elder ended (39.F.4). */
	static void hasilTantangTetua(MinecraftServer server, Perseteruan f, boolean win) {
		long today = Ingatan.today(server);
		if (!f.aktif()) {
			return;
		}
		for (UUID soul : f.jiwa) {
			SekteBus.honored(server, f.owner, soul, "mandiri.seteru.tantang_tetua", 5);
		}
		if (win) {
			f.catat(today, "riwayat.aksi.tetua.menang", Teks.argSekte(f.sekte));
			kabari(server, f, Teks.t("pesan.tetua.menang", Teks.sekte(f.sekte)));
			f.panas = 0;
			TokohSekte tokoh = SeteruData.get(server).tokoh(f.tokohUtama());
			Tangga.akhiri(server, f, tokoh != null && tokoh.punya(Sifat.BERANI) ? SeteruIds.SAINGAN : SeteruIds.DAMAI, "akhir.tetua");
		} else {
			f.catat(today, "riwayat.aksi.tetua.kalah", Teks.argSekte(f.sekte));
			kabari(server, f, Teks.t("pesan.tetua.kalah", Teks.sekte(f.sekte)));
			DiplomasiMandiri.insiden(server, f.owner, f.sekte, -5, "tetua");
			Tangga.panas(server, f, 10, "riwayat.panas_tetua");
		}
	}

	private static @Nullable Component larang(MinecraftServer server, Perseteruan f, long today) {
		f.beku = !f.beku;
		f.catat(today, f.beku ? "riwayat.aksi.larang" : "riwayat.aksi.larang_cabut", Teks.argSekte(f.sekte));
		if (f.beku) {
			for (UUID soul : f.jiwa) {
				MuridInfo murid = MuridInfo.dari(f.owner, soul);
				if (murid.punya(Sifat.AMBISIUS) || murid.punya(Sifat.SOMBONG)) {
					Antrean.ingat(server, f.owner, soul, "mandiri.seteru.dilarang", f.owner, -2, SeteruIds.k("ingat.dilarang"),
							Teks.argSekte(f.sekte));
				}
			}
		}
		kabari(server, f, Teks.t(f.beku ? "pesan.larang" : "pesan.larang_cabut", Teks.sekte(f.sekte)));
		return null;
	}

	private static @Nullable Component biarkan(MinecraftServer server, Perseteruan f, long today) {
		f.catat(today, "riwayat.aksi.biarkan", Teks.arg(f.namaUtama()));
		kabari(server, f, Teks.t("pesan.biarkan", f.namaUtama()));
		return null;
	}

	/** The away store brings the elder back ({@code Halls.addReturnHandler(AWAY_SETERU)}): beside the stele. */
	static @Nullable Vec3 kembali(ServerLevel level, UUID owner, CompoundTag stored, CompoundTag info) {
		MinecraftServer server = level.getServer();
		SeteruData.Aula aula = SeteruData.get(server).peek(owner);
		Perseteruan f = aula == null ? null : aula.byId(info.getStringOr("seteru", ""));
		if (f != null && f.tetua != null) {
			tetuaPulang(server, f);
			SeteruData.get(server).setDirty();
		}
		return null;
	}

	private CampurTangan() {
	}
}

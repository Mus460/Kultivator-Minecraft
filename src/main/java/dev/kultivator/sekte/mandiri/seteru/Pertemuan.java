package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.PertemuanProvider;
import dev.kultivator.sekte.mandiri.api.PertemuanProvider.HasilPertemuan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The encounter source of 39F (contract C39.C3, {@link #PENYEDIA}), asked by 39C's journey engine when a stage falls due
 * (never on a tick of its own). In order:
 * <ol>
 * <li>a feud at T4 waiting to ambush one of the party (on the road): escaped, wounded, or captured 10 % (39C starts the
 * rescue, this time at their gate or camp);</li>
 * <li>the patrol gang on this road (39.F.6): careful parties ({@code AMAN}) go round (+20 % time), others are ambushed
 * 25 % of the time;</li>
 * <li>a forbidden love meeting in secret at a night market;</li>
 * <li>a member of a sect present here (39.F.2 presence): an incident by the trigger table starts or heats a feud (the
 * ladder plays its first rungs, the scenes show when a player is near);</li>
 * <li>no incident: a friendly word (attitude +15..+30, friends, perhaps a forbidden love), or a friend from that sect who
 * helps against bandits on the road.</li>
 * </ol>
 * Everything is rolled from the stage seed. The result's line goes into the travel scroll; diary entries and memories are
 * written by 39F itself (39C keeps only its own memory kinds).
 */
final class Pertemuan {

	/** Registered with {@code MandiriApi.addPertemuanProvider} by {@code Seteru.init}. */
	static final PertemuanProvider PENYEDIA = Pertemuan::coba;

	/** Extra stage time of a quarrel or a detour (ticks). */
	private static final int TAMBAH_CEKCOK = 600;
	private static final int TAMBAH_MEMUTAR = 1200;

	static @Nullable HasilPertemuan coba(PertemuanProvider.Pertemuan p) {
		if (!Seteru.aktif() || p == null) {
			return null;
		}
		MinecraftServer server = p.server();
		UUID owner = p.owner();
		UUID soul = p.soul();
		RandomSource rnd = RandomSource.create(p.benih() ^ 0x5E7E8F39L);
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		long today = p.hari();
		Tangga.hariBaru(server, owner, aula, Ingatan.today(server));
		MuridInfo leader = MuridInfo.dari(owner, soul);
		boolean road = "jalan".equals(p.tahap());
		// 1. a feud at T4 waits to ambush one of the party on the road
		if (road) {
			for (UUID member : p.rombongan()) {
				Perseteruan f = aula.milik(member);
				if (f != null && f.sergapTertunda) {
					return sergapan(server, p, f, leader, rnd);
				}
			}
			// 2. the patrol on this road
			CompoundTag patrol = Patroli.diJalan(server, owner, p.pos());
			if (patrol != null) {
				Perseteruan f = aula.byId(patrol.getStringOr("seteru", ""));
				if (f != null && f.aktif()) {
					Kehatian risk = MandiriApi.kebijakan(owner, soul).risiko();
					if (risk == Kehatian.AMAN) {
						f.catat(today, "riwayat.memutar", Teks.arg(leader.nama()));
						return new HasilPertemuan(SeteruIds.k("kisah.memutar"), List.of(leader.nama().getString(), Teks.sekte(f.sekte).getString()),
								JenisLuka.TIDAK, 0L, TAMBAH_MEMUTAR, List.of(), null);
					}
					SeteruConfig config = SeteruConfig.get();
					if (rnd.nextDouble() < config.kali(config.peluang.sergapPatroli)) {
						return sergapan(server, p, f, leader, rnd);
					}
				}
			}
		}
		// 3. a forbidden love at the night market
		if ("pasar_malam".equals(p.tahap())) {
			String key = AsmaraLintas.rahasia(server, owner, leader, p.pos(), rnd);
			if (key != null) {
				return new HasilPertemuan(key, List.of(leader.nama().getString()), JenisLuka.TIDAK, 0L, 0, List.of(), null);
			}
		}
		// 4. a sect present here
		String sekte = Insiden.hadir(server, owner, p.pos(), p.tahap(), rnd);
		if (sekte == null || DiplomasiMandiri.bawahan(server, owner, sekte)) {
			return null;
		}
		ServerLevel level = Adegan.level(server, owner);
		long town = Towns.near(level, p.pos(), 260);
		String lokasi = Teks.argKota(town);
		SekteApi.diplomasi().meet(owner, sekte);
		Insiden.Hasil h = Insiden.roll(server, p, leader, sekte, rnd);
		String sectName = Teks.sekte(sekte).getString();
		if (h != null) {
			h.tokoh().dijumpai(today, lokasi);
			Perseteruan f = Tangga.mulai(server, owner, leader, h.tokoh(), h.sebab(), h.salah(), h.panas(), town, p.pos());
			data.setDirty();
			if (f == null) {
				// the hall is full of feuds: only a sour moment
				Tangga.harian(server, owner, soul, "seteru.tegang", "harian.tegang", EntriHarian.Suasana.KESAL, false, h.tokoh().argNama(),
						Teks.argSekte(sekte));
				return new HasilPertemuan(SeteruIds.k("kisah.tegang"), List.of(leader.nama().getString(), h.tokoh().kunciNama(), sectName),
						JenisLuka.TIDAK, 0L, 0, List.of(), null);
			}
			if (SeteruIds.SEBAB_DESA.equals(h.sebab())) {
				MandiriApi.kabar(server, owner, soul, SeteruIds.k("kabar.desa"), false, leader.nama().getString(), sectName);
			}
			return new HasilPertemuan(SeteruIds.k("kisah." + h.sebab()), List.of(leader.nama().getString(), h.tokoh().kunciNama(), sectName),
					JenisLuka.TIDAK, 0L, TAMBAH_CEKCOK, List.of(), null);
		}
		// 5. no incident: a friend helps, or a friendly word
		TokohSekte t = Roster.pilih(server, sekte, soul, today, rnd, false);
		if (t == null) {
			return null;
		}
		t.dijumpai(today, lokasi);
		data.setDirty();
		if (road && AsmaraLintas.sahabat(server, owner, soul, t) && rnd.nextDouble() < 0.5D) {
			Antrean.ingat(server, owner, soul, SeteruIds.ING_DITOLONG, null, 4, SeteruIds.k("ingat.ditolong"), t.argNama());
			Tangga.harian(server, owner, soul, "seteru.sahabat", "harian.sahabat_bantu", EntriHarian.Suasana.SENANG, false, t.argNama(),
					Teks.argSekte(sekte));
			return new HasilPertemuan(SeteruIds.k("kisah.sahabat_bantu"), List.of(leader.nama().getString(), t.kunciNama(), sectName),
					JenisLuka.TIDAK, 0L, 0, List.of(), null);
		}
		if (!road && !"kedai_teh".equals(p.tahap()) && !"gerbang_sekte".equals(p.tahap())) {
			return null;
		}
		Sikap sikap = SekteApi.diplomasi().stance(owner, sekte);
		boolean warm = leader.punya(Sifat.PENYAYANG) || leader.punya(Sifat.RENDAH_HATI) || t.punya(Sifat.PENYAYANG) || t.punya(Sifat.RENDAH_HATI);
		SeteruConfig config = SeteruConfig.get();
		boolean friendlyStance = sikap == Sikap.NETRAL || sikap == Sikap.RAMAH || sikap == Sikap.SEKUTU;
		if (warm && friendlyStance && rnd.nextDouble() < config.kali(config.peluang.sahabat)) {
			AsmaraLintas.ramah(server, owner, leader, t, rnd);
			Tangga.harian(server, owner, soul, "seteru.ramah", "harian.ramah", EntriHarian.Suasana.SENANG, false, t.argNama(), Teks.argSekte(sekte));
			return new HasilPertemuan(SeteruIds.k("kisah.ramah"), List.of(leader.nama().getString(), t.kunciNama(), sectName), JenisLuka.TIDAK,
					0L, 0, List.of(), null);
		}
		// a friend of the other sex met again while the sects are cold: perhaps a forbidden love
		if (AsmaraLintas.sahabat(server, owner, soul, t) && AsmaraLintas.bisaKekasih(server, owner, leader, t) && rnd.nextDouble() < 0.30D) {
			AsmaraLintas.mulai(server, owner, leader, t);
			return new HasilPertemuan(SeteruIds.k("kisah.kekasih"), List.of(leader.nama().getString(), t.kunciNama(), sectName), JenisLuka.TIDAK,
					0L, 0, List.of(), null);
		}
		return null;
	}

	/** T4 / the patrol: the gang waits on the road (39.F.3, 39.F.6). */
	private static HasilPertemuan sergapan(MinecraftServer server, PertemuanProvider.Pertemuan p, Perseteruan f, MuridInfo leader,
			RandomSource rnd) {
		long today = Ingatan.today(server);
		f.sergapTertunda = false;
		SeteruData data = SeteruData.get(server);
		TokohSekte t = data.tokoh(f.tokohUtama());
		int tier = SekteApi.diplomasi().tier(f.sekte);
		double theirs = t == null ? 1.5D : t.kekuatan(today, tier) + 0.25D;
		double escape = 0.45D + 0.12D * (leader.kekuatan() - theirs) + 0.03D * Math.max(0, p.rombongan().size() - 1);
		escape = Math.max(0.10D, Math.min(0.90D, escape));
		String sectName = Teks.sekte(f.sekte).getString();
		String them = t == null ? "" : t.kunciNama();
		List<String> args = new ArrayList<>(List.of(leader.nama().getString(), them, sectName));
		if (rnd.nextDouble() < escape) {
			f.catat(today, "riwayat.sergap.lolos", Teks.arg(leader.nama()), Teks.argSekte(f.sekte));
			Tangga.harian(server, p.owner(), p.soul(), "seteru.sergap", "harian.sergap.lolos", EntriHarian.Suasana.BANGGA, true, them,
					Teks.argSekte(f.sekte));
			Tangga.panas(server, f, 3, "riwayat.panas_sergap");
			return new HasilPertemuan(SeteruIds.k("kisah.sergap.lolos"), args, JenisLuka.TIDAK, 0L, 400, List.of(), null);
		}
		boolean captured = rnd.nextDouble() < 0.10D;
		for (UUID member : p.rombongan()) {
			Antrean.ingat(server, p.owner(), member, SeteruIds.ING_DISERGAP, null, -4, SeteruIds.k("ingat.disergap"), them);
		}
		f.catat(today, captured ? "riwayat.sergap.tertawan" : "riwayat.sergap.terluka", Teks.arg(leader.nama()), Teks.argSekte(f.sekte));
		Tangga.harian(server, p.owner(), p.soul(), "seteru.sergap", captured ? "harian.sergap.tertawan" : "harian.sergap.terluka",
				EntriHarian.Suasana.SEDIH, true, them, Teks.argSekte(f.sekte));
		Tangga.panas(server, f, 6, "riwayat.panas_sergap");
		if (!captured) {
			Tangga.lukai(server, f, p.soul(), 2);
		}
		Tangga.beritahu(server, f, Teks.t(captured ? "pesan.sergap.tertawan" : "pesan.sergap.terluka", leader.nama(), Teks.sekte(f.sekte)),
				true, true);
		return new HasilPertemuan(SeteruIds.k(captured ? "kisah.sergap.tertawan" : "kisah.sergap.terluka"), args,
				captured ? JenisLuka.TERTAWAN : JenisLuka.TERLUKA, 0L, 600, List.of(), null);
	}

	/** The stage name of a position for the test command {@code temui} (a town counts as its tea house). */
	static String tahapDi(ServerLevel level, BlockPos pos) {
		return Towns.within(level, pos) != 0L ? "kedai_teh" : "jalan";
	}

	private Pertemuan() {
	}
}

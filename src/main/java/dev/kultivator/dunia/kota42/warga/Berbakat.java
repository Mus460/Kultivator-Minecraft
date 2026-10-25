package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.data.RootGrade;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Gifted children and sect hopefuls (DESIGN 42.4.9). A child with a spirit root of LOW or better shows it from eight: a
 * faint aura while sitting ({@link #aura}), a habit of staring at the sky at dusk ({@link #melamun}) and a gossip ("the
 * Wang child makes leaves float"). At eight its family's attitude is drawn from the parents' temperaments: proud 40 %
 * (wants the sect), doubtful 35 % (needs persuading or a blessing gift), refusing 25 % (wants an heir; needs the "Restu
 * Ayah" quest, or the child runs away at fifteen when its mood is below -30). Rival NPC sects take a MID-or-better child
 * not recruited by a player within 10 days after its twelfth birthday (40 %, with news). {@link #jadikanCalon} turns a
 * resident into a hall candidate ({@code Halls.CandidateSpec}, source {@code kota:<situs>}); {@code AnakBerbakat} (38DS),
 * {@code HariGerbang} and the recruitment office (42E) read the children from {@code WargaApi.berbakat}.
 */
public final class Berbakat {

	/** {@code Warga.sikapKeluarga}. */
	public static final int TIDAK_ADA = 0;
	public static final int BANGGA = 1;
	public static final int RAGU = 2;
	public static final int MENOLAK = 3;

	/** Record keys. */
	static final String K_12 = "bk12";
	static final String K_SAINGAN = "bkSaingan";
	static final String K_KABUR = "bkKabur";
	static final String K_RESTU = "bkRestu";
	static final String K_MURID = "bkMurid";

	/** Days after the twelfth birthday before a rival sect may come. */
	public static final int SAINGAN_HARI = 10;

	// ================================================================== readers

	/** True for a child that shows its root: root LOW or better ({@code akar} >= 2), 8-15, living here. */
	public static boolean tampak(Warga w) {
		int umur = WargaApi.umur(w);
		return w.status == Warga.Status.HIDUP && w.akar >= 2 && umur >= 8 && umur <= 15;
	}

	/** Aura strength 0-1 of a body ({@code KultivatorNpc.FLAG_AURA} while sitting, 42A {@code Pemanggil}); 0 = none. */
	public static float aura(Warga w) {
		return tampak(w) ? w.akar / 5.0F : 0.0F;
	}

	/** True when the family still has to agree (doubtful without blessing; refusing without blessing nor running away). */
	public static boolean perluRestu(Warga w) {
		CompoundTag t = Pekerjaan.catatan(w);
		boolean restu = t != null && t.getBooleanOr(K_RESTU, false);
		boolean kabur = t != null && t.getBooleanOr(K_KABUR, false);
		return switch (w.sikapKeluarga) {
			case RAGU -> !restu;
			case MENOLAK -> !restu && !kabur;
			default -> false;
		};
	}

	/** True for a refusing family's child who ran off to find a sect itself (the office may take it without consent). */
	public static boolean kabur(Warga w) {
		CompoundTag t = Pekerjaan.catatan(w);
		return t != null && t.getBooleanOr(K_KABUR, false);
	}

	/** The family gave its blessing (persuaded, a blessing gift, the "Restu Ayah" quest). */
	public static void beriRestu(MinecraftServer server, Warga w) {
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		c.warga(w.id).putBoolean(K_RESTU, true);
		c.ubah();
	}

	/** {@code kultivator.kota42.bakat.sikap.<bangga|ragu|menolak|belum>}. */
	public static Component namaSikap(int sikap) {
		String id = switch (sikap) {
			case BANGGA -> "bangga";
			case RAGU -> "ragu";
			case MENOLAK -> "menolak";
			default -> "belum";
		};
		return Component.translatable("kultivator.kota42.bakat.sikap." + id);
	}

	// ================================================================== the dreamy child

	/** A gifted child staring at the sky at dusk (17-18, every other day by a hash). */
	public static @Nullable Kegiatan melamun(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam) {
		if (jam != 17 || w.akar < 2) {
			return null;
		}
		int umur = WargaApi.umur(w);
		long hari = Math.floorDiv(waktu, 24000L);
		if (umur < 8 || umur > 15 || ((hari ^ w.id) & 1L) != 0L) {
			return null;
		}
		Jadwal.Cuaca c = huni.cuaca();
		if (c.hujan() || c.badai()) {
			return null;
		}
		return Kegiatan.di("berbakat_melamun", Kegiatan.JALAN + Math.floorMod(w.id, 9), GerakId.TATAP_LANGIT, 0);
	}

	// ================================================================== Hari Warga

	/** Hari Warga step: attitudes at 8, the twelfth birthday clock, rival sects at 12 + 10 days, running away at 15. */
	public static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaConfig.warga().aktif) {
			return;
		}
		WargaData data = WargaData.get(server);
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		ServerLevel level = server.overworld();
		RandomSource random = RandomSource.create(s.id * 0x5851F42D4C957F2DL ^ hari * 0x9E3779B97F4A7C15L ^ 0x42414BL);
		int tahun = KotaConfig.warga().hariPerTahun;
		for (Warga w : List.copyOf(s.warga)) {
			if (w.status != Warga.Status.HIDUP || w.akar < 2) {
				continue;
			}
			int umur = WargaApi.umur(w);
			boolean ultah = umur != Kebiasaan.umurPada(w, hari - 1L, tahun);
			CompoundTag t = c.warga(w.id);
			if (umur >= 8 && w.sikapKeluarga == TIDAK_ADA) {
				w.sikapKeluarga = sikap(data, w, random);
				GosipWarga.lahir(level, s.id, "OPINI", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.kata("berbakat"));
			}
			if (ultah && umur == 12) {
				t.putLong(K_12, hari);
			}
			if (w.akar >= 3 && umur >= 12 && t.contains(K_12) && !t.getBooleanOr(K_SAINGAN, false)
					&& hari - t.getLongOr(K_12, hari) >= SAINGAN_HARI) {
				t.putBoolean(K_SAINGAN, true);
				if (random.nextDouble() < 0.40D && saingan(server, data, s, w, hari)) {
					continue;
				}
			}
			if (ultah && umur == 15 && w.sikapKeluarga == MENOLAK && !t.getBooleanOr(K_RESTU, false) && w.suasana < -30) {
				t.putBoolean(K_KABUR, true);
				GosipWarga.lahir(level, s.id, "OPINI", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.kata("kabur"));
			}
		}
		c.ubah();
	}

	/** The family's attitude from the parents' temperaments (base 40/35/25). */
	static int sikap(WargaData data, Warga w, RandomSource random) {
		int bangga = 40;
		int ragu = 35;
		int tolak = 25;
		for (int id : new int[] {w.ayah, w.ibu}) {
			Warga o = id == Warga.TANPA ? null : data.byId(id);
			if (o == null) {
				continue;
			}
			switch (o.watak) {
				case 0, 3 -> {
					bangga += 10;
					tolak -= 8;
				}
				case 2, 5 -> {
					tolak += 10;
					bangga -= 8;
				}
				case 4 -> ragu += 10;
				case 1 -> ragu += 5;
				default -> {
				}
			}
			// a family with a flourishing business wants its heir
			if (Pekerjaan.bekerja(o) && o.ahli >= 70) {
				tolak += 5;
			}
		}
		int r = random.nextInt(Math.max(1, bangga + ragu + Math.max(0, tolak)));
		return r < bangga ? BANGGA : r < bangga + ragu ? RAGU : MENOLAK;
	}

	/** A rival NPC sect takes the child (archived as "sekte:<id>", news at the place). False when no sect is known. */
	private static boolean saingan(MinecraftServer server, WargaData data, WargaData.Situs s, Warga w, long hari) {
		DiplomasiService d = SekteApi.diplomasi();
		BlockPos pusat = new BlockPos(s.stat.getIntOr(Pekerjaan.S_X, 0), 64, s.stat.getIntOr(Pekerjaan.S_Z, 0));
		String sekte = d.nearest(pusat, -1);
		if (sekte == null) {
			List<String> semua = d.sects();
			if (semua.isEmpty()) {
				return false;
			}
			sekte = semua.get(Math.floorMod(w.id, semua.size()));
		}
		GosipWarga.lahir(server.overworld(), s.id, "DIREKRUT", Fakta.Rujuk.warga(w.id), new Fakta.Rujuk(Fakta.Jenis.SEKTE, 0L, sekte));
		Pekerjaan.keluar(server, s.id, w, "sekte");
		ArsipWarga.arsipkan(data, s.id, w, ArsipWarga.SEBAB_SEKTE + sekte, hari, -1);
		data.buang(w.id);
		ArsipWarga.catatan(server).lupakan(w.id);
		return true;
	}

	// ================================================================== joining a player's sect

	/**
	 * A hall candidate made from {@code w} for {@code owner} (42.4.9 "Konversi"): the root grade from {@code akar}, resolve
	 * from the family (proud +10, a blessed refusal -5, a runaway +15), the name and home town on the card, source
	 * {@code kota:<situs>}, tested when it came through the recruitment office ({@code sumber} starting with
	 * {@code kantor}). The resident becomes {@code SEKTE} (kept with its family for 39's visits) and gets a living archive
	 * entry. Null when it cannot join (no root, not here, the family still has to agree).
	 */
	public static @Nullable Halls.CandidateSpec jadikanCalon(MinecraftServer server, Warga w, UUID owner, String sumber) {
		WargaData data = WargaData.get(server);
		long situs = data.situsDari(w.id);
		if (w.status != Warga.Status.HIDUP || w.akar < 2 || situs == 0L || perluRestu(w)) {
			return null;
		}
		RandomSource random = RandomSource.create(w.id * 0x9E3779B97F4A7C15L ^ owner.getLeastSignificantBits());
		DiscipleData d = DiscipleData.fresh(random);
		d.setRootGrade(RootGrade.values()[Mth.clamp(w.akar - 1, 0, RootGrade.values().length - 1)]);
		int bonus = switch (w.sikapKeluarga) {
			case BANGGA -> 10;
			case MENOLAK -> kabur(w) ? 15 : -5;
			default -> 0;
		};
		long hari = KotaHidup.hari(server);
		String asal = WargaApi.nama(w).getString() + " · " + KotaNames.town(situs).getString();
		Pekerjaan.keluar(server, situs, w, "sekte");
		w.status = Warga.Status.SEKTE;
		w.hariStatus = hari;
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		c.warga(w.id).putString(K_MURID, owner.toString());
		c.ubah();
		ArsipWarga.arsipkan(data, situs, w, ArsipWarga.SEBAB_MURID + owner, hari, -1);
		data.ubah();
		boolean diuji = sumber != null && sumber.startsWith("kantor");
		return new Halls.CandidateSpec(d, -1, -1.0D, -1.0D, diuji, "kota:" + situs, asal, bonus);
	}

	private Berbakat() {
	}
}

package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One stored person of a place (DESIGN 42.4.1-42.4.2): the third layer above the 38 slot ({@link Penduduk}) and the
 * temporary body ({@code KultivatorNpc}). A Warga occupies at most one slot ({@link #slot}; {@link #TANPA_SLOT} while
 * travelling, a baby, or at home without a body); one slot holds at most one Warga. Saved in {@code WargaData} under its
 * place with the one/two-letter keys of 42.13.1. Ids are global and never reused ({@code WargaData.nextId}).
 * <p>
 * Skeleton of 42A0: every field of 42.13.1 with load/save and small readers. 42A (G1) owns the behaviour: generation
 * ({@code WargaGen}), life cycle, bonds, moods, habits, work and moves. K9 (42.16): {@link #pasangan} only ever joins one
 * man and one woman.
 */
public final class Warga {

	/** {@link #slot} of a Warga without a slot. */
	public static final int TANPA_SLOT = -1;
	/** {@link #ayah}/{@link #ibu}/{@link #pasangan} "nobody". */
	public static final int TANPA = 0;
	/** Most children kept in {@link #anak}. */
	public static final int ANAK_MAKS = 8;

	/** {@link #kelamin} values (same as {@code sosok} body sex). */
	public static final byte PRIA = 0;
	public static final byte WANITA = 1;

	/** Life status (key {@code t}). */
	public enum Status {
		/** Lives in the place. */
		HIDUP,
		/** On the road to another place ({@link #tujuanSitus}, arriving {@link #tiba}). */
		JALAN,
		/** Joined a sect (kept as a living archive entry "disciple in sect X"; family stays). */
		SEKTE,
		/** Dead (moved to the archive by 42A). */
		MATI;

		private static final Status[] ALL = values();

		public static Status byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : HIDUP;
		}
	}

	/** {@code i}: global id (> 0). */
	public final int id;
	/** {@code s}: the slot ({@code Penduduk.id}) this Warga occupies in its place, or {@link #TANPA_SLOT}. */
	public int slot = TANPA_SLOT;
	/** {@code k}: {@link #PRIA} or {@link #WANITA}. */
	public byte kelamin;
	/** {@code l}: birth as world day x 10 (fractions of a Fana Year). */
	public long lahir;
	/** {@code m}: family name index 0-99 (Bai Jia Xing order). */
	public int marga;
	/** {@code n}: given name index (pair of syllables of the sex's list). */
	public int nama;
	/** {@code g}: generation (zibei poem index = generasi % 8). */
	public int generasi;
	/** {@code a}/{@code b}/{@code p}: father, mother, spouse ids ({@link #TANPA} = none). */
	public int ayah;
	public int ibu;
	public int pasangan;
	/** {@code c}: children ids (at most {@link #ANAK_MAKS}). */
	public int[] anak = new int[0];
	/** {@code r}: household id (residents of one house share it). */
	public int rumahTangga;
	/** {@code h}: house slot ({@code Penduduk.rumah}), {@code Penduduk.TANPA} = none. */
	public int rumah = Penduduk.TANPA;
	/** {@code j}: work role ({@code NpcRole.id()}). */
	public String peran = NpcRole.WARGA.id();
	/** {@code o}: Sosok look seed (inherited 50/50). */
	public int look;
	/** {@code v}: voice 0-5. */
	public int suara;
	/** {@code w}: temperament index of {@code Tokoh.WATAK} (ramah, curiga, serakah, bijak, penakut, sombong, jujur, licik). */
	public int watak;
	/** {@code x}/{@code y}: habits 1-32 (0 = none), 42.4.12. */
	public int kebiasaan1;
	public int kebiasaan2;
	/** {@code z}: spirit root (0 = mortal, 1-5 = {@code RootGrade} ordinal + 1). */
	public int akar;
	/** {@code q}: realm ordinal (0 = mortal). */
	public int ranah;
	/** {@code e}: work skill 0-100. */
	public int ahli;
	/** {@code d}: health 0-100. */
	public int kesehatan = 80;
	/** {@code u}: base mood -100..100 (personal layer, recomputed on Hari Warga). */
	public int suasana;
	/** {@code t}/{@code T}: status and the world day it began. */
	public Status status = Status.HIDUP;
	public long hariStatus;
	/** {@code L}: bonds packed by {@code JenisHubungan.kemas}. */
	public long[] hubungan = new long[0];
	/** {@code I}: memories of players (at most {@code kotaWarga.ingatanMaks}). */
	public final List<Ingatan> ingatan = new ArrayList<>();
	/** {@code G}: gossip ids this Warga knows. */
	public int[] gosip = new int[0];
	/** {@code tk}: story character id ("" = none; 38.S.2 / 38.T / 42.10 tokoh). */
	public String tokoh = "";
	/** {@code Z}: zhuazhou result (0 = not yet, 1-8 = the picked object). */
	public int zhuazhou;
	/** {@code S}: family attitude to a gifted child (0 none, 1 BANGGA, 2 RAGU, 3 MENOLAK). */
	public int sikapKeluarga;
	/** {@code J}: travel target place and arrival world day (status {@link Status#JALAN}). */
	public long tujuanSitus;
	public long tiba;
	/** {@code sf}: the look seed of the service body ({@code HidupApi.tubuhStaf}) this Warga stands for, 0 = none (42.4.14). */
	public int staf;
	/** {@code dt}: world day this Warga arrived in its place (newcomer, mover, -1 = born or raised here). */
	public long datang = -1L;
	/** {@code tn}/{@code tw}: betrothed to (id, {@link #TANPA} = none) and the world day of the wedding (42.4.6). */
	public int tunangan;
	public long hariNikah;
	/** {@code mh}: world day of the (last) marriage, -1 = never married. */
	public long menikahHari = -1L;
	/** {@code pw}: world day the spouse died, -1 = never widowed (remarriage waits two Fana Years). */
	public long jandaHari = -1L;
	/** {@code ab}: abadi cerita: a story character that never ages nor dies of age (42.4.3; 42B sets it). */
	public boolean abadi;

	public Warga(int id) {
		this.id = id;
	}

	// ------------------------------------------------------------------ readers

	public boolean pria() {
		return kelamin == PRIA;
	}

	public boolean hidup() {
		return status == Status.HIDUP || status == Status.JALAN;
	}

	public boolean bertubuhMungkin() {
		return status == Status.HIDUP && slot != TANPA_SLOT;
	}

	public NpcRole peranNpc() {
		NpcRole role = NpcRole.byId(peran);
		return role == null ? NpcRole.WARGA : role;
	}

	/** The memory of the player at table index {@code pemain}, or null. */
	public @Nullable Ingatan ingatanDari(int pemain) {
		for (Ingatan i : ingatan) {
			if (i.pemain == pemain) {
				return i;
			}
		}
		return null;
	}

	/** The bond with {@code idLain}, or 0 when none. */
	public long hubunganDengan(int idLain) {
		for (long b : hubungan) {
			if ((int) (b >>> 32) == idLain) {
				return b;
			}
		}
		return 0L;
	}

	/** True while this Warga can marry: alive, here, not married, not betrothed. */
	public boolean lajang() {
		return status == Status.HIDUP && pasangan == TANPA && tunangan == TANPA;
	}

	/** Sets (or replaces) the bond with {@code idLain}; when full the weakest non-family bond gives way (cap {@code maks}). */
	public void setHubungan(long bond, int maks) {
		int other = (int) (bond >>> 32);
		for (int i = 0; i < hubungan.length; i++) {
			if ((int) (hubungan[i] >>> 32) == other) {
				hubungan[i] = bond;
				return;
			}
		}
		if (hubungan.length < maks) {
			long[] next = java.util.Arrays.copyOf(hubungan, hubungan.length + 1);
			next[hubungan.length] = bond;
			hubungan = next;
			return;
		}
		int weakest = -1;
		int weakestAbs = Math.abs((byte) (bond >>> 16 & 0xFF));
		for (int i = 0; i < hubungan.length; i++) {
			int abs = Math.abs((byte) (hubungan[i] >>> 16 & 0xFF));
			if (abs < weakestAbs) {
				weakestAbs = abs;
				weakest = i;
			}
		}
		if (weakest >= 0) {
			hubungan[weakest] = bond;
		}
	}

	/** Drops the bond with {@code idLain}. */
	public void hapusHubungan(int idLain) {
		int n = 0;
		long[] keep = new long[hubungan.length];
		for (long b : hubungan) {
			if ((int) (b >>> 32) != idLain) {
				keep[n++] = b;
			}
		}
		if (n != hubungan.length) {
			hubungan = java.util.Arrays.copyOf(keep, n);
		}
	}

	public void tambahAnak(int idAnak) {
		for (int c : anak) {
			if (c == idAnak) {
				return;
			}
		}
		if (anak.length < ANAK_MAKS) {
			int[] next = java.util.Arrays.copyOf(anak, anak.length + 1);
			next[anak.length] = idAnak;
			anak = next;
		}
	}

	// ------------------------------------------------------------------ codec (42.13.1 keys)

	public CompoundTag simpan() {
		CompoundTag t = new CompoundTag();
		t.putInt("i", id);
		t.putInt("s", slot);
		t.putByte("k", kelamin);
		t.putLong("l", lahir);
		t.putByte("m", (byte) marga);
		t.putShort("n", (short) nama);
		t.putByte("g", (byte) generasi);
		putIf(t, "a", ayah);
		putIf(t, "b", ibu);
		putIf(t, "p", pasangan);
		if (anak.length > 0) {
			t.putIntArray("c", anak.clone());
		}
		putIf(t, "r", rumahTangga);
		t.putInt("h", rumah);
		t.putString("j", peran);
		t.putInt("o", look);
		t.putByte("v", (byte) suara);
		t.putByte("w", (byte) watak);
		putIf(t, "x", kebiasaan1);
		putIf(t, "y", kebiasaan2);
		putIf(t, "z", akar);
		putIf(t, "q", ranah);
		t.putByte("e", (byte) ahli);
		t.putByte("d", (byte) kesehatan);
		t.putByte("u", (byte) suasana);
		t.putByte("t", (byte) status.ordinal());
		if (hariStatus != 0L) {
			t.putLong("T", hariStatus);
		}
		if (hubungan.length > 0) {
			t.putLongArray("L", hubungan.clone());
		}
		if (!ingatan.isEmpty()) {
			ListTag list = new ListTag();
			for (Ingatan i : ingatan) {
				list.add(i.simpan());
			}
			t.put("I", list);
		}
		if (gosip.length > 0) {
			t.putIntArray("G", gosip.clone());
		}
		if (!tokoh.isEmpty()) {
			t.putString("tk", tokoh);
		}
		putIf(t, "Z", zhuazhou);
		putIf(t, "S", sikapKeluarga);
		putIf(t, "sf", staf);
		if (datang >= 0L) {
			t.putLong("dt", datang);
		}
		if (tunangan != TANPA) {
			t.putInt("tn", tunangan);
			t.putLong("tw", hariNikah);
		}
		if (menikahHari >= 0L) {
			t.putLong("mh", menikahHari);
		}
		if (jandaHari >= 0L) {
			t.putLong("pw", jandaHari);
		}
		if (abadi) {
			t.putBoolean("ab", true);
		}
		if (status == Status.JALAN) {
			CompoundTag j = new CompoundTag();
			j.putLong("s", tujuanSitus);
			j.putLong("t", tiba);
			t.put("J", j);
		}
		return t;
	}

	public static Warga muat(CompoundTag t) {
		Warga w = new Warga(t.getIntOr("i", 0));
		w.slot = t.getIntOr("s", TANPA_SLOT);
		w.kelamin = t.getByteOr("k", PRIA) == WANITA ? WANITA : PRIA;
		w.lahir = t.getLongOr("l", 0L);
		w.marga = Mth.clamp(t.getByteOr("m", (byte) 0), 0, 99);
		w.nama = t.getShortOr("n", (short) 0) & 0xFFFF;
		w.generasi = t.getByteOr("g", (byte) 0) & 0xFF;
		w.ayah = t.getIntOr("a", TANPA);
		w.ibu = t.getIntOr("b", TANPA);
		w.pasangan = t.getIntOr("p", TANPA);
		w.anak = t.getIntArray("c").orElse(new int[0]);
		w.rumahTangga = t.getIntOr("r", 0);
		w.rumah = t.getIntOr("h", Penduduk.TANPA);
		w.peran = t.getStringOr("j", NpcRole.WARGA.id());
		w.look = t.getIntOr("o", 0);
		w.suara = Mth.clamp(t.getByteOr("v", (byte) 0), 0, 5);
		w.watak = Mth.clamp(t.getByteOr("w", (byte) 0), 0, 7);
		w.kebiasaan1 = t.getIntOr("x", 0);
		w.kebiasaan2 = t.getIntOr("y", 0);
		w.akar = Mth.clamp(t.getIntOr("z", 0), 0, 5);
		w.ranah = Math.max(0, t.getIntOr("q", 0));
		w.ahli = Mth.clamp(t.getByteOr("e", (byte) 0), 0, 100);
		w.kesehatan = Mth.clamp(t.getByteOr("d", (byte) 80), 0, 100);
		w.suasana = Mth.clamp(t.getByteOr("u", (byte) 0), -100, 100);
		w.status = Status.byOrdinal(t.getByteOr("t", (byte) 0));
		w.hariStatus = t.getLongOr("T", 0L);
		w.hubungan = t.getLongArray("L").orElse(new long[0]);
		ListTag list = t.getListOrEmpty("I");
		for (int i = 0; i < list.size(); i++) {
			w.ingatan.add(Ingatan.muat(list.getCompoundOrEmpty(i)));
		}
		w.gosip = t.getIntArray("G").orElse(new int[0]);
		w.tokoh = t.getStringOr("tk", "");
		w.zhuazhou = t.getIntOr("Z", 0);
		w.sikapKeluarga = t.getIntOr("S", 0);
		w.staf = t.getIntOr("sf", 0);
		w.datang = t.getLongOr("dt", -1L);
		w.tunangan = t.getIntOr("tn", TANPA);
		w.hariNikah = t.getLongOr("tw", 0L);
		w.menikahHari = t.getLongOr("mh", -1L);
		w.jandaHari = t.getLongOr("pw", -1L);
		w.abadi = t.getBooleanOr("ab", false);
		CompoundTag j = t.getCompoundOrEmpty("J");
		w.tujuanSitus = j.getLongOr("s", 0L);
		w.tiba = j.getLongOr("t", 0L);
		return w;
	}

	private static void putIf(CompoundTag t, String key, int value) {
		if (value != 0) {
			t.putInt(key, value);
		}
	}
}

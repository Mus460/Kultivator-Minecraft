package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.TujuanProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One journey (DESIGN 39.6.2): the plan made once at the gate and everything its stages resolved so far, as a live view
 * over its compound in {@link JalanData}. The plan holds absolute game times, so a body shown at any moment finds the
 * stage it is in; each stage is resolved once when it falls due ({@link Tahap}) with the seed
 * {@code soul ^ departure ^ index}, so the same journey always tells the same story.
 * <p>
 * A party shares one plan: the leader's record carries the stages and results, each member's record only
 * {@code ikut = <leader soul>} and its own return. Results are kept per soul ({@code untuk}), applied by the return
 * handler of that soul ({@link Kepulangan}).
 */
public final class Rencana {

	// keys
	static final String ID = "id";
	static final String OWNER = "owner";
	static final String SOUL = "soul";
	static final String NAMA = "nama";
	static final String IKUT = "ikut";
	static final String JENIS = "jenis";
	static final String TUJUAN = "tujuan";
	static final String DIM = "dim";
	static final String AULA = "aula";
	static final String BERANGKAT = "berangkat";
	static final String BENIH = "benih";
	static final String MODA = "moda";
	static final String ROMBONGAN = "rombongan";
	static final String ANGGOTA = "anggota";
	static final String TAHAP = "tahap";
	static final String I = "i";
	static final String HASIL = "hasil";
	static final String PULANG = "pulang";
	static final String TAWANAN = "tawanan";
	static final String DIPANGGIL = "dipanggil";
	static final String TIBA_POS = "tibaPos";
	static final String PERTEMUAN = "pertemuan";
	static final String TAMBAHAN = "tambahan";
	static final String TEL = "tel";

	/** Ways to travel (39.6.2). */
	public static final String KAKI = "kaki";
	public static final String PEDANG = "pedang";
	public static final String AWAN = "awan";
	/** By the town teleport station: afoot to the nearest station, the ride, afoot again (and the same way home). */
	public static final String TELEPORT = "teleport";

	private final CompoundTag tag;

	Rencana(CompoundTag tag) {
		this.tag = tag;
	}

	public CompoundTag tag() {
		return tag;
	}

	// ================================================================== identity

	public String id() {
		return tag.getStringOr(ID, "");
	}

	public UUID owner() {
		return uuid(tag.getStringOr(OWNER, ""));
	}

	public UUID soul() {
		return uuid(tag.getStringOr(SOUL, ""));
	}

	/** The leading soul of a member's record, or null for the leader. */
	public @Nullable UUID ikut() {
		String s = tag.getStringOr(IKUT, "");
		return s.isEmpty() ? null : uuid(s);
	}

	public String nama() {
		return tag.getStringOr(NAMA, "");
	}

	public JenisJalan jenis() {
		JenisJalan j = JenisJalan.byId(tag.getStringOr(JENIS, ""));
		return j == null ? JenisJalan.BELANJA : j;
	}

	public TujuanProvider.Tujuan tujuan() {
		CompoundTag t = tag.getCompoundOrEmpty(TUJUAN);
		JenisJalan j = JenisJalan.byId(t.getStringOr("jenis", ""));
		return new TujuanProvider.Tujuan(t.getStringOr("id", ""), j == null ? jenis() : j, BlockPos.of(t.getLongOr("pos", 0L)),
				t.getIntOr("tier", 1), t.getStringOr("nama", ""), t.getLongOr("town", 0L), t.contains("situs") ? t.getStringOr("situs", "") : null);
	}

	static CompoundTag tujuanTag(TujuanProvider.Tujuan t) {
		CompoundTag one = new CompoundTag();
		one.putString("id", t.id());
		one.putString("jenis", t.jenis().id());
		one.putLong("pos", t.pos().asLong());
		one.putInt("tier", t.tier());
		one.putString("nama", t.nama());
		one.putLong("town", t.town());
		if (t.situs() != null) {
			one.putString("situs", t.situs());
		}
		return one;
	}

	/** The packed town centre of a town journey (0 otherwise). */
	public long town() {
		return tag.getCompoundOrEmpty(TUJUAN).getLongOr("town", 0L);
	}

	public String dim() {
		return tag.getStringOr(DIM, "");
	}

	public BlockPos aula() {
		return BlockPos.of(tag.getLongOr(AULA, 0L));
	}

	public long berangkat() {
		return tag.getLongOr(BERANGKAT, 0L);
	}

	public long benih() {
		return tag.getLongOr(BENIH, 0L);
	}

	public String moda() {
		return tag.getStringOr(MODA, KAKI);
	}

	// ================================================================== party

	/** Every travelling soul, leader first. */
	public List<UUID> rombongan() {
		ListTag list = tag.getListOrEmpty(ROMBONGAN);
		List<UUID> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			UUID u = uuid(list.getStringOr(i, ""));
			if (u != null) {
				out.add(u);
			}
		}
		return out;
	}

	/**
	 * What the plan knows of one member at the gate: {@code {soul, nama, band, power, uang, ruang, sifat (bitmask of
	 * Sifat ordinals), jalan, iblis, perempuan, kehatian}}.
	 */
	public CompoundTag anggota(UUID soul) {
		return tag.getCompoundOrEmpty(ANGGOTA).getCompoundOrEmpty(soul.toString());
	}

	/** The party's strength P (39.6.9): the strongest member's band + 0.25 x the sum of the others'. */
	public double kekuatan() {
		double best = 0.0D;
		double rest = 0.0D;
		for (UUID soul : rombongan()) {
			double p = anggota(soul).getDoubleOr("power", 0.0D);
			if (p > best) {
				rest += best;
				best = p;
			} else {
				rest += p;
			}
		}
		return best + 0.25D * rest;
	}

	/** True when a party member is of Golden Core or above (+0.05 per stage). */
	public boolean adaInti() {
		for (UUID soul : rombongan()) {
			if (anggota(soul).getIntOr("band", 0) >= 3) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== stages

	public ListTag tahap() {
		ListTag list = tag.getList(TAHAP).orElse(null);
		if (list == null) {
			list = new ListTag();
			tag.put(TAHAP, list);
		}
		return list;
	}

	public int jumlahTahap() {
		return tahap().size();
	}

	public CompoundTag tahap(int index) {
		return tahap().getCompoundOrEmpty(index);
	}

	/** Index of the next stage to resolve. */
	public int berikut() {
		return tag.getIntOr(I, 0);
	}

	void setBerikut(int index) {
		tag.putInt(I, index);
	}

	/** True once every stage is resolved. */
	public boolean selesai() {
		return berikut() >= jumlahTahap();
	}

	/** The stage running at game time {@code now} (resolved or not), or -1 before the first and after the last. */
	public int tahapPada(long now) {
		ListTag list = tahap();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			if (now >= t.getLongOr("mulai", 0L) && now < t.getLongOr("sampai", 0L)) {
				return i;
			}
		}
		return -1;
	}

	/** Moves every stage from {@code from} on by {@code delta} ticks (an encounter, captivity) and the return. */
	void geser(int from, long delta) {
		if (delta == 0L) {
			return;
		}
		ListTag list = tahap();
		for (int i = from; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			if (i == from) {
				t.putLong("sampai", Math.max(t.getLongOr("mulai", 0L) + 20L, t.getLongOr("sampai", 0L) + delta));
			} else {
				t.putLong("mulai", t.getLongOr("mulai", 0L) + delta);
				t.putLong("sampai", t.getLongOr("sampai", 0L) + delta);
			}
		}
		setPulang(pulang() + delta);
	}

	/** Inserts {@code stage} after {@code after}; every later stage and the return move by its length. */
	void sisip(int after, CompoundTag stage) {
		long len = Math.max(0L, stage.getLongOr("sampai", 0L) - stage.getLongOr("mulai", 0L));
		ListTag list = tahap();
		for (int i = after + 1; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			t.putLong("mulai", t.getLongOr("mulai", 0L) + len);
			t.putLong("sampai", t.getLongOr("sampai", 0L) + len);
		}
		list.add(Math.min(list.size(), after + 1), stage);
		setPulang(pulang() + len);
	}

	/** Drops every stage after {@code index} (recalled home, captured with no road left). */
	void potong(int index) {
		ListTag list = tahap();
		while (list.size() > index + 1) {
			list.remove(list.size() - 1);
		}
		kembalikanTarifPulang();
	}

	/** The teleport plan {@code {tarif, asal (station block), kotaAsal, pulang (the return ride is paid and still ahead)}}, or empty. */
	public CompoundTag tel() {
		return tag.getCompoundOrEmpty(TEL);
	}

	/**
	 * A plan cut short (recall, captivity) that no longer holds its return ride gives that fare back to every member's purse,
	 * once: the fare of both rides was taken at the gate.
	 */
	private void kembalikanTarifPulang() {
		CompoundTag t = tel();
		if (!t.getBooleanOr("pulang", false)) {
			return;
		}
		ListTag list = tahap();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag s = list.getCompoundOrEmpty(i);
			if (Tahap.TELEPORT.equals(s.getStringOr("jenis", "")) && s.getIntOr("arah", 0) == 1) {
				return;
			}
		}
		t.putBoolean("pulang", false);
		long fare = t.getLongOr("tarif", 0L);
		for (UUID soul : rombongan()) {
			ubahUang(soul, fare);
		}
	}

	// ================================================================== return

	/** Planned arrival at the hall (game time). */
	public long pulang() {
		return tag.getLongOr(PULANG, 0L);
	}

	void setPulang(long time) {
		tag.putLong(PULANG, time);
	}

	public boolean dipanggil() {
		return tag.getBooleanOr(DIPANGGIL, false);
	}

	/** Where an early-recalled disciple appears (the town body's spot), or null. */
	public @Nullable BlockPos tibaPos() {
		return tag.contains(TIBA_POS) ? BlockPos.of(tag.getLongOr(TIBA_POS, 0L)) : null;
	}

	void panggil(BlockPos at) {
		tag.putBoolean(DIPANGGIL, true);
		tag.putLong(TIBA_POS, at.asLong());
	}

	/** The captivity record {@code {pos, sampai, situs, site, tebusan}} or an empty tag. */
	public CompoundTag tawanan() {
		return tag.getCompoundOrEmpty(TAWANAN);
	}

	public boolean tertawan() {
		return !tawanan().isEmpty() && !tawanan().getBooleanOr("bebas", false);
	}

	// ================================================================== results

	/** The results so far {@code {tahap[], barang[], uang.<soul>, luka.<soul>, kenangan[], jam, pk.<soul>, sukses}}. */
	public CompoundTag hasil() {
		CompoundTag h = tag.getCompound(HASIL).orElse(null);
		if (h == null) {
			h = new CompoundTag();
			tag.put(HASIL, h);
		}
		return h;
	}

	/** Appends a stage line of the travel scroll ({@code key|arg|arg}). */
	void kisah(String key, List<String> args) {
		ListTag list = hasil().getList("tahap").orElse(null);
		if (list == null) {
			list = new ListTag();
			hasil().put("tahap", list);
		}
		if (list.size() < 24) {
			list.add(StringTag.valueOf(JalanIds.tahap(key, args)));
		}
	}

	/** Goods for {@code untuk}: {@code ke} tas/lumbung/guru/hadiah:<soul>, price paid in total. */
	void barang(UUID untuk, String item, int n, String ke, long hargaTotal, boolean dibeli) {
		ListTag list = hasil().getList("barang").orElse(null);
		if (list == null) {
			list = new ListTag();
			hasil().put("barang", list);
		}
		CompoundTag one = new CompoundTag();
		one.putString("untuk", untuk.toString());
		one.putString("item", item);
		one.putInt("n", n);
		one.putString("ke", ke);
		one.putLong("harga", hargaTotal);
		one.putBoolean("beli", dibeli);
		list.add(one);
	}

	public ListTag daftarBarang() {
		return hasil().getListOrEmpty("barang");
	}

	/** The purse change of {@code soul} so far (spent negative). */
	public long uang(UUID soul) {
		return hasil().getCompoundOrEmpty("uang").getLongOr(soul.toString(), 0L);
	}

	/** Changes the purse of {@code soul}; never below the purse it left with plus what it was given. */
	void ubahUang(UUID soul, long delta) {
		CompoundTag u = child(hasil(), "uang");
		u.putLong(soul.toString(), u.getLongOr(soul.toString(), 0L) + delta);
	}

	/** Money earned by {@code soul} (missions, sales, road rewards): goes through the earning rules at home. */
	public long pendapatan(UUID soul) {
		return hasil().getCompoundOrEmpty("dapat").getLongOr(soul.toString(), 0L);
	}

	void tambahPendapatan(UUID soul, long nb) {
		CompoundTag u = child(hasil(), "dapat");
		u.putLong(soul.toString(), u.getLongOr(soul.toString(), 0L) + Math.max(0L, nb));
	}

	/** The purse {@code soul} can still spend on this journey. */
	public long sisaUang(UUID soul) {
		return Math.max(0L, anggota(soul).getLongOr("uang", 0L) + uang(soul) + tambahan(soul));
	}

	/** NB the master put into the purse in town ([Tambah uang]). */
	public long tambahan(UUID soul) {
		return hasil().getCompoundOrEmpty(TAMBAHAN).getLongOr(soul.toString(), 0L);
	}

	void tambahUangGuru(UUID soul, long nb) {
		CompoundTag u = child(hasil(), TAMBAHAN);
		u.putLong(soul.toString(), u.getLongOr(soul.toString(), 0L) + nb);
	}

	/** The worst wound of {@code soul} so far. */
	public JenisLuka luka(UUID soul) {
		return JenisLuka.byId(hasil().getCompoundOrEmpty("luka").getStringOr(soul.toString(), ""));
	}

	void lukai(UUID soul, JenisLuka luka) {
		if (luka == null || luka.ordinal() <= luka(soul).ordinal()) {
			return;
		}
		child(hasil(), "luka").putString(soul.toString(), luka.id());
	}

	void sembuhkan(UUID soul) {
		child(hasil(), "luka").remove(soul.toString());
	}

	void kenangan(String kind) {
		ListTag list = hasil().getList("kenangan").orElse(null);
		if (list == null) {
			list = new ListTag();
			hasil().put("kenangan", list);
		}
		if (list.size() < 8) {
			list.add(StringTag.valueOf(kind));
		}
	}

	/** K-weighted hours of cultivation the journey gave (applied at arrival). */
	public double jam() {
		return hasil().getDoubleOr("jam", 0.0D);
	}

	void tambahJam(double hours) {
		hasil().putDouble("jam", jam() + Math.max(0.0D, hours));
	}

	public int pk(UUID soul) {
		return hasil().getCompoundOrEmpty("pk").getIntOr(soul.toString(), 0);
	}

	void tambahPk(UUID soul, int pk) {
		CompoundTag u = child(hasil(), "pk");
		u.putInt(soul.toString(), u.getIntOr(soul.toString(), 0) + Math.max(0, pk));
	}

	public boolean sukses() {
		return hasil().getBooleanOr("sukses", true);
	}

	void setSukses(boolean value) {
		hasil().putBoolean("sukses", value);
	}

	/** Encounters of other sects answered so far (39.F.2: at most one per journey). */
	public int pertemuan() {
		return tag.getIntOr(PERTEMUAN, 0);
	}

	void tambahPertemuan() {
		tag.putInt(PERTEMUAN, pertemuan() + 1);
	}

	/** NB {@code soul} spent on goods this journey. */
	public long nilaiBelanja(UUID soul) {
		long sum = 0L;
		ListTag list = daftarBarang();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			if (soul.toString().equals(one.getStringOr("untuk", "")) && one.getBooleanOr("beli", false)) {
				sum += one.getLongOr("harga", 0L);
			}
		}
		return sum;
	}

	// ================================================================== helpers

	static @Nullable UUID uuid(String s) {
		try {
			return s == null || s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static CompoundTag child(CompoundTag parent, String key) {
		CompoundTag c = parent.getCompound(key).orElse(null);
		if (c == null) {
			c = new CompoundTag();
			parent.put(key, c);
		}
		return c;
	}
}

package dev.kultivator.sekte.diplomasi;

import dev.kultivator.sekte.api.Lambang;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One NPC sect of the world (DESIGN 36.2.9): founded from the seeded {@link GerbangPlan} (name, alignment, emblem,
 * starting tier 1-5, gate position and size band), then grown by {@link Simulasi} (resources, tier up to 9, feuds with
 * other NPC sects). Its relations with each player hall live in {@link Relasi}. Server only; pure data.
 */
public final class SekteNpc {

	public static final int LURUS = 0;
	public static final int NETRAL = 1;
	public static final int IBLIS = 2;
	public static final String[] ALIRAN = {"lurus", "netral", "iblis"};
	public static final int MAX_TIER = 9;
	/** Defectors remembered per sect (they may appear among its gate members). */
	public static final int MAX_PEMBELOT = 6;

	/** Gate state: not seen yet, its anchor loaded (born in a new chunk), or known missing. */
	public static final int GERBANG_BELUM = 0;
	public static final int GERBANG_LAHIR = 1;

	final int index;
	final String id;
	int aliran;
	int pola;
	int kataA;
	int kataB;
	int band;
	int raiderSect;
	int agresi;
	int kehormatan;
	int ketuaMarga;
	int ketuaNama;
	int lambang;
	int blockX;
	int blockZ;
	int tier;
	int sumber;
	long grownDay = -1L;
	int gateY = Integer.MIN_VALUE;
	int gateState = GERBANG_BELUM;
	/** The player hall this sect became a vassal of (after its master was defeated), or null. */
	@Nullable UUID tuan;
	int ketuaKalah;
	/** The NPC sect it feuds with (index), -1 none; and until which day. */
	int musuhNpc = -1;
	long musuhSampai;
	/** Defectors: {soul, name}. */
	final List<CompoundTag> pembelot = new ArrayList<>();

	SekteNpc(int index) {
		this.index = index;
		this.id = "s" + index;
	}

	/** A sect fresh from the plan. */
	static SekteNpc founded(GerbangPlan.Entry e) {
		SekteNpc s = new SekteNpc(e.index());
		s.aliran = e.aliran();
		s.pola = e.pola();
		s.kataA = e.kataA();
		s.kataB = e.kataB();
		s.band = e.band();
		s.raiderSect = e.raiderSect();
		s.agresi = e.agresi();
		s.kehormatan = e.kehormatan();
		s.ketuaMarga = e.ketuaMarga();
		s.ketuaNama = e.ketuaNama();
		s.lambang = Lambang.seeded(e.lambangSeed()).pack();
		s.blockX = e.blockX();
		s.blockZ = e.blockZ();
		s.tier = e.tier();
		s.sumber = 20 * e.tier();
		return s;
	}

	public String id() {
		return id;
	}

	/**
	 * The Saingan rival group that is this sect's travelling band (T12: one list, so sect {@code s<i>} always walks with
	 * group {@code g<i>}; nothing to link or store).
	 */
	public String rivalLink() {
		return DaftarSekte.rivalId(index);
	}

	public int index() {
		return index;
	}

	public Component name() {
		return GeneratorNama.sect(pola, kataA, kataB);
	}

	public Component master() {
		return GeneratorNama.master(ketuaMarga, ketuaNama);
	}

	public Lambang emblem() {
		return Lambang.unpack(lambang);
	}

	public int tier() {
		return tier;
	}

	public int aliran() {
		return aliran;
	}

	public boolean iblis() {
		return aliran == IBLIS;
	}

	/** {@code tier × 100 + resources} (DESIGN 36.2.9). */
	public int strength() {
		return tier * 100 + Math.max(0, sumber);
	}

	/** Resources needed for the next tier ({@code 150 × tier}). */
	public int nextTierCost() {
		return 150 * tier;
	}

	/** The planned gate (y from the anchor when born, else 64). */
	public BlockPos gate() {
		return new BlockPos(blockX, gateY == Integer.MIN_VALUE ? 64 : gateY, blockZ);
	}

	public boolean gateBorn() {
		return gateState == GERBANG_LAHIR;
	}

	/**
	 * The sect master's rank step ({@code Rank.ofStep}) for its body: {@code 4 + 2 × tier}, so tier 1-2 Foundation, 3-4
	 * Golden Core, 5-6 Nascent Soul, 7-8 Deity Transformation and tier 9 Dao Union.
	 */
	public int masterRankStep() {
		return 4 + 2 * tier;
	}

	/** A member's rank step: three to six steps below the master (never below Qi Refining). */
	public int memberRankStep(int slot) {
		return Math.max(2, masterRankStep() - 3 - Math.floorMod(slot * 7 + index, 4));
	}

	void addPembelot(UUID soul, Component name) {
		pembelot.removeIf(t -> t.getStringOr("soul", "").equals(soul.toString()));
		CompoundTag t = new CompoundTag();
		t.putString("soul", soul.toString());
		t.putString("name", name.getString());
		pembelot.add(t);
		while (pembelot.size() > MAX_PEMBELOT) {
			pembelot.remove(0);
		}
	}

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("i", index);
		t.putInt("aliran", aliran);
		t.putInt("pola", pola);
		t.putInt("a", kataA);
		t.putInt("b", kataB);
		t.putInt("band", band);
		t.putInt("raider", raiderSect);
		t.putInt("agresi", agresi);
		t.putInt("hormat", kehormatan);
		t.putInt("marga", ketuaMarga);
		t.putInt("nama", ketuaNama);
		t.putInt("lambang", lambang);
		t.putInt("x", blockX);
		t.putInt("z", blockZ);
		t.putInt("tier", tier);
		t.putInt("sumber", sumber);
		t.putLong("grown", grownDay);
		t.putInt("gy", gateY);
		t.putInt("gs", gateState);
		if (tuan != null) {
			t.putString("tuan", tuan.toString());
		}
		t.putInt("kalah", ketuaKalah);
		t.putInt("musuh", musuhNpc);
		t.putLong("musuh_sampai", musuhSampai);
		ListTag list = new ListTag();
		list.addAll(pembelot);
		t.put("pembelot", list);
		return t;
	}

	static SekteNpc load(CompoundTag t) {
		SekteNpc s = new SekteNpc(t.getIntOr("i", 0));
		s.aliran = Math.floorMod(t.getIntOr("aliran", 0), 3);
		s.pola = t.getIntOr("pola", 0);
		s.kataA = t.getIntOr("a", 0);
		s.kataB = t.getIntOr("b", 0);
		s.band = Math.floorMod(t.getIntOr("band", 0), 3);
		s.raiderSect = t.getIntOr("raider", 0);
		s.agresi = t.getIntOr("agresi", 30);
		s.kehormatan = t.getIntOr("hormat", 50);
		s.ketuaMarga = t.getIntOr("marga", 0);
		s.ketuaNama = t.getIntOr("nama", 0);
		s.lambang = t.getIntOr("lambang", Lambang.DEFAULT.pack());
		s.blockX = t.getIntOr("x", 0);
		s.blockZ = t.getIntOr("z", 0);
		s.tier = Math.max(1, Math.min(MAX_TIER, t.getIntOr("tier", 1)));
		s.sumber = t.getIntOr("sumber", 0);
		s.grownDay = t.getLongOr("grown", -1L);
		s.gateY = t.getIntOr("gy", Integer.MIN_VALUE);
		s.gateState = t.getIntOr("gs", GERBANG_BELUM);
		String tuan = t.getStringOr("tuan", "");
		if (!tuan.isEmpty()) {
			try {
				s.tuan = UUID.fromString(tuan);
			} catch (IllegalArgumentException ignored) {
				s.tuan = null;
			}
		}
		s.ketuaKalah = t.getIntOr("kalah", 0);
		s.musuhNpc = t.getIntOr("musuh", -1);
		s.musuhSampai = t.getLongOr("musuh_sampai", 0L);
		ListTag list = t.getListOrEmpty("pembelot");
		for (int i = 0; i < list.size(); i++) {
			s.pembelot.add(list.getCompoundOrEmpty(i));
		}
		return s;
	}
}

package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleNames;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sosok.Sosok;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One named disciple of an NPC sect (DESIGN 39.F.1), kept in {@link SeteruData}'s world roster: the same person for every
 * player hall. Seeded from {@code (world seed, sect, slot)} by {@link Roster}; only the meetings change it (attitude
 * toward each of our disciples, grudges, a love, wounds, a nickname earned in a fight, defection). About 200-300 bytes.
 * <p>
 * The realm grows lazily: {@link #langkah(long, int)} adds one rank step per {@link #HARI_PER_LANGKAH} world days since
 * the member joined, never past the cap of its sect tier. Server thread only.
 */
final class TokohSekte {

	/** World days per rank step of growth (39.F.1: one stage every 8 days). */
	static final int HARI_PER_LANGKAH = 8;
	/** Roles: outer, inner, core disciple, Eldest Senior Brother/Sister (one per sect), Young Elder (tier 6+). */
	static final int PERAN_LUAR = 0;
	static final int PERAN_DALAM = 1;
	static final int PERAN_INTI = 2;
	static final int PERAN_KAKAK = 3;
	static final int PERAN_TETUA = 4;
	static final String[] PERAN = {"luar", "dalam", "inti", "kakak", "tetua"};
	/** Nicknames earned in fights ({@code julukan.<i>}). */
	static final int JULUKAN = 16;
	/** Weapons ({@code senjata.<i>}): sword, sabre, spear, iron fan, twin daggers, whip. */
	static final int SENJATA = 6;
	static final int DENDAM_MAKS = 3;

	final String id;
	final String sekte;
	final int slot;
	int nama;
	int julukan = -1;
	int peran;
	int langkahAwal;
	long hariLahir;
	final List<Sifat> sifat = new ArrayList<>(2);
	String dao = "";
	int senjata;
	String suara = "m1";
	int postur;
	int benih;
	final Map<String, Integer> sikap = new LinkedHashMap<>();
	final List<String> dendam = new ArrayList<>();
	String kasih = "";
	long lukaSampai;
	int jumpa;
	long jumpaHari = -1L;
	String jumpaLokasi = "";
	String status = SeteruIds.TOKOH_AKTIF;

	TokohSekte(String sekte, int slot) {
		this.sekte = sekte;
		this.slot = slot;
		this.id = sekte + "#" + slot;
	}

	// ================================================================== reads

	/** The disciple name key ({@code kultivator.disciples.name.<i>}). */
	String kunciNama() {
		return DiscipleNames.key(nama);
	}

	/** {@link Sosok#LAKI} or {@link Sosok#PEREMPUAN}, from the name (so names always match the body). */
	int kelamin() {
		return DiscipleNames.kelamin(nama);
	}

	boolean perempuan() {
		return kelamin() == Sosok.PEREMPUAN;
	}

	/** The plain name, or "Name 'Nickname'" once a nickname was earned. */
	MutableComponent namaTampil() {
		MutableComponent name = Component.translatable(kunciNama());
		if (julukan >= 0) {
			return Component.translatable(SeteruIds.k("nama_julukan"), name, Component.translatable(SeteruIds.k("julukan." + julukan)));
		}
		return name;
	}

	/** The diary argument for the name (a lang key, translated by the reader's client). */
	String argNama() {
		return kunciNama();
	}

	String kunciPeran() {
		return SeteruIds.k("peran." + PERAN[Math.max(0, Math.min(PERAN.length - 1, peran))]);
	}

	/** The rank step on world day {@code hari} (grown lazily, capped by the sect tier). */
	int langkah(long hari, int tierSekte) {
		long grown = Math.max(0L, hari - hariLahir) / HARI_PER_LANGKAH;
		int cap = batasLangkah(tierSekte);
		return (int) Math.max(0L, Math.min(Math.min(cap, Rank.MAX_STEP), langkahAwal + grown));
	}

	/** The cap of growth: one realm (4 steps) above the sect tier's member band, Young Elders a little higher. */
	int batasLangkah(int tierSekte) {
		int master = 4 + 2 * Math.max(1, tierSekte);
		return peran == PERAN_TETUA ? master - 1 : peran == PERAN_KAKAK ? master - 2 : master - 3;
	}

	Rank rank(long hari, int tierSekte) {
		return Rank.ofStep(langkah(hari, tierSekte));
	}

	/** Strength in realm units (rank step / 4), compared with our disciples' for the data duels. */
	double kekuatan(long hari, int tierSekte) {
		return langkah(hari, tierSekte) / 4.0D;
	}

	boolean punya(Sifat s) {
		return sifat.contains(s);
	}

	/** Wounded (badly beaten) and so not met until the day passes. */
	boolean luka(long hari) {
		return hari < lukaSampai;
	}

	boolean aktif() {
		return SeteruIds.TOKOH_AKTIF.equals(status);
	}

	int sikap(UUID soul) {
		Integer value = sikap.get(soul.toString());
		return value == null ? 0 : value;
	}

	void ubahSikap(UUID soul, int delta) {
		String key = soul.toString();
		int now = Math.max(-100, Math.min(100, sikap(soul) + delta));
		sikap.put(key, now);
		// at most 12 remembered souls: forget the most indifferent first
		while (sikap.size() > 12) {
			String weakest = null;
			int low = Integer.MAX_VALUE;
			for (Map.Entry<String, Integer> e : sikap.entrySet()) {
				if (!e.getKey().equals(key) && Math.abs(e.getValue()) < low) {
					low = Math.abs(e.getValue());
					weakest = e.getKey();
				}
			}
			if (weakest == null) {
				break;
			}
			sikap.remove(weakest);
		}
	}

	boolean benci(UUID soul) {
		return dendam.contains(soul.toString());
	}

	void tambahDendam(UUID soul) {
		String key = soul.toString();
		dendam.remove(key);
		dendam.add(key);
		while (dendam.size() > DENDAM_MAKS) {
			dendam.remove(0);
		}
	}

	void hapusDendam(UUID soul) {
		dendam.remove(soul.toString());
	}

	void dijumpai(long hari, @Nullable String lokasi) {
		jumpa++;
		jumpaHari = hari;
		if (lokasi != null && !lokasi.isEmpty()) {
			jumpaLokasi = lokasi;
		}
	}

	// ================================================================== save / load

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putString("sekte", sekte);
		t.putInt("slot", slot);
		t.putInt("nama", nama);
		if (julukan >= 0) {
			t.putInt("julukan", julukan);
		}
		t.putInt("peran", peran);
		t.putInt("langkah", langkahAwal);
		t.putLong("lahir", hariLahir);
		ListTag traits = new ListTag();
		for (Sifat s : sifat) {
			traits.add(StringTag.valueOf(s.id()));
		}
		t.put("sifat", traits);
		t.putString("dao", dao);
		t.putInt("senjata", senjata);
		t.putString("suara", suara);
		t.putInt("postur", postur);
		t.putInt("benih", benih);
		CompoundTag att = new CompoundTag();
		for (Map.Entry<String, Integer> e : sikap.entrySet()) {
			att.putInt(e.getKey(), e.getValue());
		}
		t.put("sikap", att);
		ListTag grudges = new ListTag();
		for (String soul : dendam) {
			grudges.add(StringTag.valueOf(soul));
		}
		t.put("dendam", grudges);
		if (!kasih.isEmpty()) {
			t.putString("kasih", kasih);
		}
		if (lukaSampai > 0L) {
			t.putLong("luka", lukaSampai);
		}
		t.putInt("jumpa", jumpa);
		t.putLong("jumpaHari", jumpaHari);
		if (!jumpaLokasi.isEmpty()) {
			t.putString("lokasi", jumpaLokasi);
		}
		t.putString("status", status);
		return t;
	}

	static TokohSekte load(CompoundTag t) {
		TokohSekte s = new TokohSekte(t.getStringOr("sekte", "s0"), t.getIntOr("slot", 0));
		s.nama = t.getIntOr("nama", 0);
		s.julukan = t.getIntOr("julukan", -1);
		s.peran = t.getIntOr("peran", PERAN_LUAR);
		s.langkahAwal = t.getIntOr("langkah", 2);
		s.hariLahir = t.getLongOr("lahir", 0L);
		ListTag traits = t.getListOrEmpty("sifat");
		for (int i = 0; i < traits.size(); i++) {
			Sifat sifat = Sifat.byId(traits.getStringOr(i, ""));
			if (sifat != null) {
				s.sifat.add(sifat);
			}
		}
		s.dao = t.getStringOr("dao", "");
		s.senjata = t.getIntOr("senjata", 0);
		s.suara = t.getStringOr("suara", "m1");
		s.postur = t.getIntOr("postur", 0);
		s.benih = t.getIntOr("benih", 0);
		CompoundTag att = t.getCompoundOrEmpty("sikap");
		for (String key : att.keySet()) {
			s.sikap.put(key, att.getIntOr(key, 0));
		}
		ListTag grudges = t.getListOrEmpty("dendam");
		for (int i = 0; i < grudges.size(); i++) {
			s.dendam.add(grudges.getStringOr(i, ""));
		}
		s.kasih = t.getStringOr("kasih", "");
		s.lukaSampai = t.getLongOr("luka", 0L);
		s.jumpa = t.getIntOr("jumpa", 0);
		s.jumpaHari = t.getLongOr("jumpaHari", -1L);
		s.jumpaLokasi = t.getStringOr("lokasi", "");
		s.status = t.getStringOr("status", SeteruIds.TOKOH_AKTIF);
		return s;
	}
}

package dev.kultivator.sekte.mandiri.seteru;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One feud of a player hall with an NPC sect (DESIGN 39.F.3): {@code Seteru(id, owner, jiwaKita[] <= 3, sektenya,
 * tokohMereka[] <= 3, sebab, salah, panas 0-100, tingkat, hariMulai, riwayat <= 8, status)} plus the bookkeeping of the
 * master's actions (39.F.4), the sworn vow, the elder sent to make peace and the scene waiting to be shown. Kept in
 * {@link SeteruData} under its owner. Server thread only.
 */
final class Perseteruan {

	static final int JIWA_MAKS = 3;
	static final int RIWAYAT_MAKS = 8;

	final String id;
	final UUID owner;
	final String sekte;
	final List<UUID> jiwa = new ArrayList<>(JIWA_MAKS);
	final List<Component> namaJiwa = new ArrayList<>(JIWA_MAKS);
	final List<String> tokoh = new ArrayList<>(JIWA_MAKS);
	String sebab;
	String salah = SeteruIds.SALAH_TIDAK;
	int panas;
	/** The highest tier reached (-1 before T0). */
	int tingkat = -1;
	long hariMulai;
	final List<CompoundTag> riwayat = new ArrayList<>();
	String status = SeteruIds.AKTIF;
	/** The vow of revenge holds the heat until this world day (39.F.3 T5). */
	long sumpahSampai;
	/** The master forbade the place and sect: the heat is frozen. */
	boolean beku;
	/** Master's actions: action id -> world day it was last used (each once a day). */
	final CompoundTag aksiHari = new CompoundTag();
	/** An elder sent to make peace and the game time it returns. */
	@Nullable UUID tetua;
	long tetuaPulang;
	/** The master did something about this feud (no DIABAIKAN at T4). */
	boolean diurus;
	boolean diabaikan;
	/** The place where it began (site id, 0 none), the town's name key or literal, and the last scene position. */
	long situs;
	String tempat = "";
	long pos;
	/** A scene waiting to be shown live when someone is near: {kind, due, pos, situs} or empty. */
	CompoundTag adegan = new CompoundTag();
	/** The world day the heat last fell (decay once per day). */
	long hariTurun = -1L;
	/** World day the feud ended (for the screen's "ended" list). */
	long hariAkhir = -1L;
	/** The official tournament challenge was already sent at T7. */
	boolean resmi;
	/** T4 waits for the next journey of a party member to ambush it (39.F.6). */
	boolean sergapTertunda;

	Perseteruan(String id, UUID owner, String sekte, String sebab) {
		this.id = id;
		this.owner = owner;
		this.sekte = sekte;
		this.sebab = sebab;
	}

	boolean aktif() {
		return SeteruIds.AKTIF.equals(status) || SeteruIds.TERLARANG.equals(status);
	}

	@Nullable UUID jiwaUtama() {
		return jiwa.isEmpty() ? null : jiwa.get(0);
	}

	@Nullable String tokohUtama() {
		return tokoh.isEmpty() ? null : tokoh.get(0);
	}

	Component namaUtama() {
		return namaJiwa.isEmpty() ? Component.translatable("entity.kultivator.disciple") : namaJiwa.get(0);
	}

	boolean melibatkan(UUID soul) {
		return jiwa.contains(soul);
	}

	void tambahJiwa(UUID soul, Component nama) {
		if (soul == null || jiwa.contains(soul) || jiwa.size() >= JIWA_MAKS) {
			return;
		}
		jiwa.add(soul);
		namaJiwa.add(nama == null ? Component.translatable("entity.kultivator.disciple") : nama.copy());
	}

	void tambahTokoh(String tokohId) {
		if (tokohId != null && !tokoh.contains(tokohId) && tokoh.size() < JIWA_MAKS) {
			tokoh.add(tokohId);
		}
	}

	/** Our disciple was wronged ({@code salah = mereka}): the master may demand compensation. */
	boolean korban() {
		return SeteruIds.SALAH_MEREKA.equals(salah);
	}

	boolean sumpah(long hari) {
		return hari < sumpahSampai;
	}

	/** True when action {@code aksi} was not used today. */
	boolean bolehAksi(int aksi, long hari) {
		return aksiHari.getLongOr(Integer.toString(aksi), -1L) != hari;
	}

	void pakaiAksi(int aksi, long hari) {
		aksiHari.putLong(Integer.toString(aksi), hari);
		diurus = true;
	}

	/** Appends one history line {@code {hari, kunci, arg[]}} (the oldest dropped after 8). */
	void catat(long hari, String kunci, String... arg) {
		CompoundTag line = new CompoundTag();
		line.putLong("hari", hari);
		line.putString("kunci", kunci);
		ListTag args = new ListTag();
		for (String a : arg) {
			args.add(StringTag.valueOf(a == null ? "" : a));
		}
		line.put("arg", args);
		riwayat.add(line);
		while (riwayat.size() > RIWAYAT_MAKS) {
			riwayat.remove(0);
		}
	}

	BlockPos posisi() {
		return BlockPos.of(pos);
	}

	// ================================================================== save / load

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putString("id", id);
		t.store("owner", UUIDUtil.CODEC, owner);
		t.putString("sekte", sekte);
		ListTag souls = new ListTag();
		for (int i = 0; i < jiwa.size(); i++) {
			CompoundTag s = new CompoundTag();
			s.store("soul", UUIDUtil.CODEC, jiwa.get(i));
			s.store("nama", ComponentSerialization.CODEC, i < namaJiwa.size() ? namaJiwa.get(i) : Component.empty());
			souls.add(s);
		}
		t.put("jiwa", souls);
		ListTag them = new ListTag();
		for (String one : tokoh) {
			them.add(StringTag.valueOf(one));
		}
		t.put("tokoh", them);
		t.putString("sebab", sebab);
		t.putString("salah", salah);
		t.putInt("panas", panas);
		t.putInt("tingkat", tingkat);
		t.putLong("mulai", hariMulai);
		ListTag history = new ListTag();
		history.addAll(riwayat);
		t.put("riwayat", history);
		t.putString("status", status);
		t.putLong("sumpah", sumpahSampai);
		t.putBoolean("beku", beku);
		t.put("aksi", aksiHari.copy());
		if (tetua != null) {
			t.store("tetua", UUIDUtil.CODEC, tetua);
			t.putLong("tetuaPulang", tetuaPulang);
		}
		t.putBoolean("diurus", diurus);
		t.putBoolean("diabaikan", diabaikan);
		t.putLong("situs", situs);
		t.putString("tempat", tempat);
		t.putLong("pos", pos);
		if (!adegan.isEmpty()) {
			t.put("adegan", adegan.copy());
		}
		t.putLong("hariTurun", hariTurun);
		t.putLong("hariAkhir", hariAkhir);
		t.putBoolean("resmi", resmi);
		t.putBoolean("sergap", sergapTertunda);
		return t;
	}

	static @Nullable Perseteruan load(CompoundTag t) {
		UUID owner = t.read("owner", UUIDUtil.CODEC).orElse(null);
		String id = t.getStringOr("id", "");
		if (owner == null || id.isEmpty()) {
			return null;
		}
		Perseteruan f = new Perseteruan(id, owner, t.getStringOr("sekte", "s0"), t.getStringOr("sebab", SeteruIds.SEBAB_HINAAN));
		ListTag souls = t.getListOrEmpty("jiwa");
		for (int i = 0; i < souls.size(); i++) {
			CompoundTag s = souls.getCompoundOrEmpty(i);
			UUID soul = s.read("soul", UUIDUtil.CODEC).orElse(null);
			if (soul != null) {
				f.jiwa.add(soul);
				f.namaJiwa.add(s.read("nama", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple")));
			}
		}
		ListTag them = t.getListOrEmpty("tokoh");
		for (int i = 0; i < them.size(); i++) {
			f.tokoh.add(them.getStringOr(i, ""));
		}
		f.salah = t.getStringOr("salah", SeteruIds.SALAH_TIDAK);
		f.panas = Math.max(0, Math.min(100, t.getIntOr("panas", 0)));
		f.tingkat = Math.max(-1, Math.min(7, t.getIntOr("tingkat", -1)));
		f.hariMulai = t.getLongOr("mulai", 0L);
		ListTag history = t.getListOrEmpty("riwayat");
		for (int i = 0; i < history.size(); i++) {
			f.riwayat.add(history.getCompoundOrEmpty(i));
		}
		f.status = t.getStringOr("status", SeteruIds.AKTIF);
		f.sumpahSampai = t.getLongOr("sumpah", 0L);
		f.beku = t.getBooleanOr("beku", false);
		CompoundTag acts = t.getCompoundOrEmpty("aksi");
		for (String key : acts.keySet()) {
			f.aksiHari.putLong(key, acts.getLongOr(key, -1L));
		}
		f.tetua = t.read("tetua", UUIDUtil.CODEC).orElse(null);
		f.tetuaPulang = t.getLongOr("tetuaPulang", 0L);
		f.diurus = t.getBooleanOr("diurus", false);
		f.diabaikan = t.getBooleanOr("diabaikan", false);
		f.situs = t.getLongOr("situs", 0L);
		f.tempat = t.getStringOr("tempat", "");
		f.pos = t.getLongOr("pos", 0L);
		f.adegan = t.getCompoundOrEmpty("adegan").copy();
		f.hariTurun = t.getLongOr("hariTurun", -1L);
		f.hariAkhir = t.getLongOr("hariAkhir", -1L);
		f.resmi = t.getBooleanOr("resmi", false);
		f.sergapTertunda = t.getBooleanOr("sergap", false);
		return f;
	}
}

package dev.kultivator.sekte.diplomasi;

import dev.kultivator.sekte.api.Sikap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;
import java.util.List;

/**
 * The relation of one player hall with one NPC sect (DESIGN 36.2.9): the standing -100..100, the pact (none,
 * non-aggression, alliance), the war and its raid tally, the vassal and tribute state, the cooldowns of the player's
 * actions and a short chronicle (the last {@link #KRONIK} lines) for the Diplomasi screen. Server only.
 */
public final class Relasi {

	public static final int PAKTA_TIDAK = 0;
	public static final int PAKTA_DAMAI = 1;
	public static final int PAKTA_SEKUTU = 2;
	public static final int KRONIK = 8;

	int standing;
	int pakta = PAKTA_TIDAK;
	boolean perang;
	long perangDay;
	/** Raids of this sect during the current war: sacked (a loss for the hall) and repelled. */
	int perangKalah;
	int perangMenang;
	/** The day the next war raid was asked for (so it is asked once per 3-5 days). */
	long perangSeranganDay;
	boolean ditemui;
	boolean bawahan;
	long upetiMingguDay;
	/** Forced tribute after a lost war: payments left and the next day. */
	int upetiPaksa;
	long upetiPaksaDay;
	long utusanDay = -100L;
	long hadiahDay = -100L;
	long tuntutDay = -100L;
	long paktaTolakDay = -100L;
	long tantangDay = -100L;
	long ketuaDay = -100L;
	long sekutuHadiahDay = -100L;
	final List<CompoundTag> kronik = new ArrayList<>();

	Relasi(int standing) {
		this.standing = standing;
	}

	public int standing() {
		return standing;
	}

	public Sikap sikap() {
		if (perang) {
			return Sikap.PERANG;
		}
		Sikap sikap = Sikap.of(standing, pakta == PAKTA_SEKUTU);
		// Without a declared war a standing below -70 reads as hostile, not war (war is an act, not a number).
		return sikap == Sikap.PERANG ? Sikap.BERMUSUHAN : sikap;
	}

	void add(int delta) {
		standing = Math.max(-100, Math.min(100, standing + delta));
	}

	void note(long day, Component line) {
		CompoundTag t = new CompoundTag();
		t.putLong("day", day);
		t.store("text", ComponentSerialization.CODEC, line);
		kronik.add(t);
		while (kronik.size() > KRONIK) {
			kronik.remove(0);
		}
	}

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("standing", standing);
		t.putInt("pakta", pakta);
		t.putBoolean("perang", perang);
		t.putLong("perang_day", perangDay);
		t.putInt("perang_kalah", perangKalah);
		t.putInt("perang_menang", perangMenang);
		t.putLong("perang_serangan", perangSeranganDay);
		t.putBoolean("ditemui", ditemui);
		t.putBoolean("bawahan", bawahan);
		t.putLong("upeti_minggu", upetiMingguDay);
		t.putInt("upeti_paksa", upetiPaksa);
		t.putLong("upeti_paksa_day", upetiPaksaDay);
		t.putLong("utusan", utusanDay);
		t.putLong("hadiah", hadiahDay);
		t.putLong("tuntut", tuntutDay);
		t.putLong("pakta_tolak", paktaTolakDay);
		t.putLong("tantang", tantangDay);
		t.putLong("ketua", ketuaDay);
		t.putLong("sekutu_hadiah", sekutuHadiahDay);
		ListTag list = new ListTag();
		list.addAll(kronik);
		t.put("kronik", list);
		return t;
	}

	static Relasi load(CompoundTag t) {
		Relasi r = new Relasi(t.getIntOr("standing", 0));
		r.pakta = Math.max(PAKTA_TIDAK, Math.min(PAKTA_SEKUTU, t.getIntOr("pakta", PAKTA_TIDAK)));
		r.perang = t.getBooleanOr("perang", false);
		r.perangDay = t.getLongOr("perang_day", 0L);
		r.perangKalah = t.getIntOr("perang_kalah", 0);
		r.perangMenang = t.getIntOr("perang_menang", 0);
		r.perangSeranganDay = t.getLongOr("perang_serangan", 0L);
		r.ditemui = t.getBooleanOr("ditemui", false);
		r.bawahan = t.getBooleanOr("bawahan", false);
		r.upetiMingguDay = t.getLongOr("upeti_minggu", 0L);
		r.upetiPaksa = t.getIntOr("upeti_paksa", 0);
		r.upetiPaksaDay = t.getLongOr("upeti_paksa_day", 0L);
		r.utusanDay = t.getLongOr("utusan", -100L);
		r.hadiahDay = t.getLongOr("hadiah", -100L);
		r.tuntutDay = t.getLongOr("tuntut", -100L);
		r.paktaTolakDay = t.getLongOr("pakta_tolak", -100L);
		r.tantangDay = t.getLongOr("tantang", -100L);
		r.ketuaDay = t.getLongOr("ketua", -100L);
		r.sekutuHadiahDay = t.getLongOr("sekutu_hadiah", -100L);
		ListTag list = t.getListOrEmpty("kronik");
		for (int i = 0; i < list.size(); i++) {
			r.kronik.add(list.getCompoundOrEmpty(i));
		}
		return r;
	}
}

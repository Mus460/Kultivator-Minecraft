package dev.kultivator.sekte.gabung.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The five doors into an NPC sect (DESIGN 41.5.2). Each door says which of the three entrance stages it runs (1 the
 * Test Stair, 2 the style test, 3 the heart interview), which requirements of 41.4.4 it waives and the highest rank it
 * can give. A stage the door skips counts 0.8 in the verdict (41.5.5). Pure data, safe on both sides.
 */
public enum Pintu {

	/** Ujian Gerbang: every requirement, on the sect's Test Day (Kecil: any daytime); stages 1-2-3; up to Luar. */
	GERBANG("gerbang", true, true, true, false, false, Pangkat.LUAR),
	/** Token Naik Sekte ({@code kultivator:token_naik}): no Bone Age limit; stages 2-3; up to Luar. */
	TOKEN("token", false, true, true, false, true, Pangkat.LUAR),
	/** Undangan Turnamen (top 4 of the Grand Tournament, own hall champion, an envoy's letter): stage 3; Luar (Dalam). */
	UNDANGAN("undangan", false, false, true, true, true, Pangkat.LUAR),
	/** Pintu Pelayan: no roots or Bone Age, Infami Lurus <= 30, one short question; Pelayan. */
	PELAYAN("pelayan", false, false, false, true, true, Pangkat.PELAYAN),
	/** Tetua Tamu (hall level >= 3 or realm >= master - 1, standing >= 20): the Guest Trial; Tamu. */
	TAMU("tamu", false, false, false, true, true, Pangkat.TAMU);

	private final String id;
	private final boolean tahap1;
	private final boolean tahap2;
	private final boolean tahap3;
	private final boolean tanpaAkar;
	private final boolean tanpaUsiaTulang;
	private final Pangkat pangkatMaks;

	Pintu(String id, boolean tahap1, boolean tahap2, boolean tahap3, boolean tanpaAkar, boolean tanpaUsiaTulang, Pangkat pangkatMaks) {
		this.id = id;
		this.tahap1 = tahap1;
		this.tahap2 = tahap2;
		this.tahap3 = tahap3;
		this.tanpaAkar = tanpaAkar;
		this.tanpaUsiaTulang = tanpaUsiaTulang;
		this.pangkatMaks = pangkatMaks;
	}

	public String id() {
		return id;
	}

	/** True when the door runs stage {@code tahap} (1-3). */
	public boolean jalani(int tahap) {
		return switch (tahap) {
			case 1 -> tahap1;
			case 2 -> tahap2;
			case 3 -> tahap3;
			default -> false;
		};
	}

	/** The root requirement is waived (Undangan, Pelayan, Tamu). */
	public boolean tanpaAkar() {
		return tanpaAkar;
	}

	/** The Bone Age requirement is waived (Token, Undangan, Pelayan, Tamu). */
	public boolean tanpaUsiaTulang() {
		return tanpaUsiaTulang;
	}

	/** The highest rank the verdict can give through this door (Undangan: Dalam for a Foundation+ Grand champion). */
	public Pangkat pangkatMaks() {
		return pangkatMaks;
	}

	public MutableComponent nama() {
		return Component.translatable("kultivator.gabung.pintu." + id);
	}

	public static Pintu byId(String id, Pintu fallback) {
		for (Pintu p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return fallback;
	}
}

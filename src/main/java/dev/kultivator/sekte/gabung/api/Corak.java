package dev.kultivator.sekte.gabung.api;

import dev.kultivator.data.Dao;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The ten sect styles of DESIGN 41.4.3, read from word A of the sect's name ({@code GerbangPlan.Entry.kataA}, unique per
 * plan, T12), so the style always agrees with the name: "Sekte Pedang ..." is a sword sect, "Lembah Racun ..." a poison
 * sect. Pure data, safe on both sides.
 * <p>
 * The style picks the stage-2 entrance test ({@link #ujian}), the board missions' flavour, the special daily chore, the
 * Teknik Warisan (a real existing {@code TechniqueSpec} of one of {@link #dao} at the class's best grade, chosen with
 * the sect's seed), the robe cut of 40 ({@link #gayaJubah}) and the voice lines. The emblem stays 40.3.8 item 1.
 */
public enum Corak {

	/** Pedang (0): discipline, flying swords; test Seratus Tebasan. */
	PEDANG("pedang", "seratus_tebasan", "HANFU_L", "sabuk_pedang", 0xC9D6E8, Dao.SWORD),
	/** Teratai (1), Lonceng (5): compassion, the vow not to kill; test Lorong Manusia Perunggu. */
	BUDDHA("buddha", "lorong_perunggu", "TAO", "tasbih", 0xE0A93B, Dao.LIFE, Dao.BODY),
	/** Bangau (2), Naga (6): taming, riding; test Taklukkan Binatang. */
	BINATANG("binatang", "taklukkan_binatang", "TEMPUR", "kerah_bulu", 0x8FAF5A, Dao.BODY),
	/** Giok (3), Kitab (4), Kipas (8): scholars, talismans; test Labirin Batu. */
	FORMASI("formasi", "labirin_batu", "HANFU_L", "topi_sarjana", 0x5A3FD0, Dao.SPACE),
	/** Bulan (9), Petir (12), Angin (14): control of Qi; test Lima Pilar Qi. */
	UNSUR("unsur", "lima_pilar", "HANFU_P", "selendang", 0x9FD8FF, Dao.ICE, Dao.LIGHTNING, Dao.SHADOW),
	/** Burung Hong (7), Tungku (13): pills and furnaces; test Satu Tungku. */
	ALKIMIA("alkimia", "satu_tungku", "PENDEK", "celemek_tungku", 0xFF8A3D, Dao.FIRE),
	/** Harimau (10), Tombak (11): steel bodies, spears; test Pikul Gunung. */
	TUBUH("tubuh", "pikul_gunung", "TEMPUR", "lengan_pendek", 0xC77B3B, Dao.BODY),
	/** Darah (15), Tengkorak (17): blood offerings; test Kolam Darah. */
	DARAH("darah", "kolam_darah", "TEMPUR", "rantai", 0xB0202A, Dao.BODY, Dao.FIRE),
	/** Racun (16): brews and cunning; test Tiga Cawan. */
	RACUN("racun", "tiga_cawan", "HANFU_L", "kantong_racun", 0x7A3FA0, Dao.LIFE),
	/** Hantu (18), Bayangan (19): silence and infiltration; test Ujian Senyap. */
	BAYANGAN("bayangan", "ujian_senyap", "PENDEK", "kerudung", 0x2E2A3A, Dao.SHADOW);

	/** Word A index (0-19, {@code GeneratorNama}) -> style; the table of 41.4.3. */
	private static final Corak[] DARI_KATA_A = {
			PEDANG, BUDDHA, BINATANG, FORMASI, FORMASI, BUDDHA, BINATANG, ALKIMIA, FORMASI, UNSUR,
			TUBUH, TUBUH, UNSUR, ALKIMIA, UNSUR, DARAH, RACUN, DARAH, BAYANGAN, BAYANGAN};

	private final String id;
	private final String ujian;
	private final String gayaJubah;
	private final String hiasan;
	private final int warna;
	private final Dao[] dao;

	Corak(String id, String ujian, String gayaJubah, String hiasan, int warna, Dao... dao) {
		this.id = id;
		this.ujian = ujian;
		this.gayaJubah = gayaJubah;
		this.hiasan = hiasan;
		this.warna = warna;
		this.dao = dao;
	}

	public String id() {
		return id;
	}

	/** The stage-2 test id (41.5.4); the class of 41B-C is {@code masuk/corak/<Corak>}. */
	public String ujian() {
		return ujian;
	}

	/** The base garment of the 40 look system ({@code HANFU_L}, {@code HANFU_P}, {@code TAO}, {@code TEMPUR}, {@code PENDEK}). */
	public String gayaJubah() {
		return gayaJubah;
	}

	/** The style's ornament key on the member robe (41.11.5: sword belt, rosary, fur collar, scholar cap ...), drawn by 41V. */
	public String hiasan() {
		return hiasan;
	}

	/** The style's accent colour (directory card, HUD of the entrance test). */
	public int warna() {
		return warna;
	}

	/** The style's Dao (20 % Paviliun discount on techniques of these Dao, Teknik Warisan pool). */
	public Dao[] dao() {
		return dao.clone();
	}

	/** The first Dao, the one Teknik Warisan prefers. */
	public Dao daoUtama() {
		return dao[0];
	}

	/** True when {@code d} is one of the style's Dao. */
	public boolean punyaDao(Dao d) {
		for (Dao own : dao) {
			if (own == d) {
				return true;
			}
		}
		return false;
	}

	/** Darah, Racun, Bayangan: passing their test is the Ujian Darah (Infami +10, 41.4.4). */
	public boolean ujianDarah() {
		return this == DARAH || this == RACUN || this == BAYANGAN;
	}

	public MutableComponent nama() {
		return Component.translatable("kultivator.gabung.corak." + id);
	}

	/** The one-line flavour ("disiplin, pedang terbang"). */
	public MutableComponent rasa() {
		return Component.translatable("kultivator.gabung.corak." + id + ".rasa");
	}

	public MutableComponent namaUjian() {
		return Component.translatable("kultivator.gabung.ujian." + ujian);
	}

	/** The style of word A {@code kataA}; an index outside 0-19 (a changed name list) falls back to {@link #PEDANG}. */
	public static Corak of(int kataA) {
		return kataA >= 0 && kataA < DARI_KATA_A.length ? DARI_KATA_A[kataA] : PEDANG;
	}

	/** The style of id {@code id}, or {@code fallback}. */
	public static Corak byId(String id, Corak fallback) {
		for (Corak c : values()) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		return fallback;
	}

	/** The style of ordinal {@code ordinal} (synced as a byte), clamped. */
	public static Corak byOrdinal(int ordinal) {
		Corak[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))];
	}
}

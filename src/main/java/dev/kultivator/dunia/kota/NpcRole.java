package dev.kultivator.dunia.kota;

import java.util.Locale;

/**
 * The roles of {@code kultivator:kultivator_npc} (DESIGN 34.5.1-34.5.4). Each role has a robe colour (the disciple robe
 * layer tinted), whether it stays at its anchor, its combat tier (1-4, the rogue numbers of 25.4.2), and the screen tab
 * a right-click opens ({@link KotaNet} tabs; -1 = a spoken line only). Title key {@code kultivator.kota.peran.<id>}.
 */
public enum NpcRole {
	/** Juru Lelang: the auctioneer on the Balai Lelang stage. */
	JURU_LELANG(0x7A1E22, true, 2, KotaNet.TAB_LELANG),
	/** Pemilik Kedai Teh: the tea master of the Paviliun Angin Bambu. */
	PEMILIK_TEH(0x3E6E4E, true, 2, KotaNet.TAB_TEH),
	/** Makelar Gelap: the black-market broker. */
	MAKELAR_GELAP(0x262230, true, 2, KotaNet.TAB_GELAP),
	/** Pedagang Pil: the pill pavilion's merchant. */
	PEDAGANG_PIL(0x5C7C3A, true, 1, KotaNet.TAB_TOKO),
	/** Pandai Pusaka: the treasure pavilion's smith. */
	PANDAI_PUSAKA(0x6A4A2A, true, 1, KotaNet.TAB_TOKO),
	/** Penjaga Kota: the guard sect's watchman (Foundation-Golden Core), also keeps the notice board. */
	PENJAGA(0x2E3E5C, false, 3, KotaNet.TAB_MISI),
	/** Warga: townsfolk walking between home and square. */
	WARGA(0x8A7A60, false, 1, -1),
	/** Kultivator Pengembara: a wandering cultivator (34.5.2). */
	PENGEMBARA(0x4A5A6A, false, 2, KotaNet.TAB_PERTEMUAN),
	/** A member or leader of a rival group (34.5.3). */
	SAINGAN(0x3A2A2A, false, 2, -1),
	/** An immortal native of the Immortal City (white-gold robe, True Immortal aura). */
	ABADI(0xF2E6C0, false, 4, -1),
	/** The Dao lecturer of the Paviliun Dao. */
	PENCERAMAH(0xE8D7A8, true, 4, KotaNet.TAB_KULIAH),
	/** The Pasar Abadi's merchant (Batu Asal Abadi). */
	PEDAGANG_ABADI(0xD8C890, true, 3, KotaNet.TAB_PASAR_ABADI),
	/** The stage of the Balai Lelang Abadi: opens the existing Lelang Abadi tab (no auction logic here). */
	JURU_LELANG_ABADI(0xC8A860, true, 3, KotaNet.TAB_LELANG_ABADI),
	/** The Jade Emperor's envoy in the Istana Giok: the weekly Titah Utusan. */
	UTUSAN(0xFFE9A8, true, 4, KotaNet.TAB_TITAH),
	/** The keeper of the Kebun Persik Abadi. */
	PENJAGA_KEBUN(0xF0B8C8, true, 3, KotaNet.TAB_PERSIK);

	public final int robe;
	public final boolean stationary;
	public final int tier;
	public final int tab;

	NpcRole(int robe, boolean stationary, int tier, int tab) {
		this.robe = robe;
		this.stationary = stationary;
		this.tier = tier;
		this.tab = tab;
	}

	/** "juru_lelang", "pengembara"... (commands and lang keys). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String titleKey() {
		return "kultivator.kota.peran." + id();
	}

	/** True for the roles of the Immortal City. */
	public boolean immortal() {
		return this == ABADI || this == PENCERAMAH || this == PEDAGANG_ABADI || this == JURU_LELANG_ABADI || this == UTUSAN || this == PENJAGA_KEBUN;
	}

	/** True for the town's own people (their harm lowers Nama Baik and wakes the guards). */
	public boolean townsfolk() {
		return this != PENGEMBARA && this != SAINGAN;
	}

	public static NpcRole byOrdinal(int ordinal) {
		NpcRole[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : WARGA;
	}

	public static NpcRole byId(String id) {
		for (NpcRole role : values()) {
			if (role.id().equals(id)) {
				return role;
			}
		}
		return null;
	}
}

package dev.kultivator.sekte.mandiri.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.List;

/**
 * The master's policy (DESIGN 39.7.1 plus the feud row of 39.F.4), one per hall in {@code MandiriData.kebijakan} and an
 * optional per-disciple overlay in {@code extra.mandiri.kebijakan} (only the keys present override; "Ikut aula" removes
 * the overlay). {@code MandiriApi.kebijakan(owner, soul)} returns the effective value. Immutable; the {@code with*}
 * copies are what the board and the tab edit. Pure data, safe on both sides.
 *
 * @param mode                planner mode
 * @param risiko              caution on journeys
 * @param uangSakuPersen      pocket money, 0-200 % of the rank table ({@code MandiriConfig.uangSaku})
 * @param izin                {@link IzinKegiatan} bitmask
 * @param larang              forbidden destinations: site ids or kinds ({@code "pasar_gelap"}, {@code "kota_air"} ...)
 * @param jatahLumbung        item ids a disciple may take from the Lumbung itself, one per day each
 * @param tungguTerobosanHari days the disciple waits for the master before breaking through alone
 * @param tungguDaoHari       days it waits for the master before choosing its own Dao
 * @param jenjang             who approves a self-made rank request
 * @param hatiHatiTerobosan   forbids the "hurried" breakthrough (39.5.2)
 * @param beritahu            {@link Pemberitahuan} bitmask of what is announced to the master
 * @param seteru              how the hall answers a feud (39.F.4)
 * @param titipanNbMinggu     NB per week the hall gives for sect errands bought in town
 */
public record Kebijakan(ModeMandiri mode, Kehatian risiko, int uangSakuPersen, int izin, List<String> larang,
						List<String> jatahLumbung, int tungguTerobosanHari, int tungguDaoHari, Jenjang jenjang,
						boolean hatiHatiTerobosan, int beritahu, SikapSeteru seteru, int titipanNbMinggu) {

	/** Who approves a self-made rank request (39.5.5). */
	public enum Jenjang {
		/** The Grand Elder of E if the hall has one (default), else the master. */
		TETUA_AGUNG,
		/** Approved at once. */
		OTOMATIS,
		/** Always asks the master (click in chat). */
		TANYA
	}

	/** The feud policy row (39.F.4). */
	public enum SikapSeteru {
		SELALU_DAMAIKAN,
		/** Default: ask the master at T2 and above. */
		TANYA,
		BIARKAN
	}

	/** What the master is told about (39.7.1), bits {@code 1 << ordinal()}. */
	public enum Pemberitahuan {
		TEROBOSAN, PULANG, LUKA, TERTAWAN, BELANJA_MAHAL, TEKNIK, JALAN_GANTI, IBLIS, SETERU;

		public int bit() {
			return 1 << ordinal();
		}

		/** Default: everything except purchases of 200 NB or more. */
		public static final int BAWAAN = ((1 << 9) - 1) & ~(1 << 4);
	}

	/** The Lumbung allowance of a new world: healing, qi gathering and qi recovery pills. */
	public static final List<String> JATAH_BAWAAN = List.of("kultivator:healing_pill", "kultivator:qi_gathering_pill",
			"kultivator:qi_recovery_pill");

	public Kebijakan {
		mode = mode == null ? ModeMandiri.SEIMBANG : mode;
		risiko = risiko == null ? Kehatian.WAJAR : risiko;
		uangSakuPersen = Math.max(0, Math.min(200, uangSakuPersen));
		izin &= IzinKegiatan.SEMUA;
		larang = larang == null ? List.of() : List.copyOf(larang);
		jatahLumbung = jatahLumbung == null ? List.of() : List.copyOf(jatahLumbung);
		tungguTerobosanHari = Math.max(0, Math.min(30, tungguTerobosanHari));
		tungguDaoHari = Math.max(0, Math.min(30, tungguDaoHari));
		jenjang = jenjang == null ? Jenjang.TETUA_AGUNG : jenjang;
		seteru = seteru == null ? SikapSeteru.TANYA : seteru;
		titipanNbMinggu = Math.max(0, Math.min(5000, titipanNbMinggu));
	}

	/** The new-world default for {@code mode} (39.7.1): Wajar, 100 %, all but secret realms, wait 2 / 3 days. */
	public static Kebijakan bawaan(ModeMandiri mode, int tungguGuruHari, int tungguDaoHari) {
		ModeMandiri m = mode == null ? ModeMandiri.SEIMBANG : mode;
		int tunggu = m == ModeMandiri.PENUH ? 0 : tungguGuruHari;
		int dao = m == ModeMandiri.PENUH ? 0 : tungguDaoHari;
		return new Kebijakan(m, Kehatian.WAJAR, 100, IzinKegiatan.BAWAAN, List.of(), JATAH_BAWAAN, tunggu, dao,
				Jenjang.TETUA_AGUNG, false, Pemberitahuan.BAWAAN, SikapSeteru.TANYA, 0);
	}

	public boolean boleh(IzinKegiatan group) {
		return group != null && group.boleh(izin);
	}

	public boolean diberitahu(Pemberitahuan jenis) {
		return jenis != null && (beritahu & jenis.bit()) != 0;
	}

	public boolean dilarang(String situsAtauJenis) {
		return situsAtauJenis != null && larang.contains(situsAtauJenis);
	}

	public Kebijakan withMode(ModeMandiri value) {
		return new Kebijakan(value, risiko, uangSakuPersen, izin, larang, jatahLumbung, tungguTerobosanHari, tungguDaoHari,
				jenjang, hatiHatiTerobosan, beritahu, seteru, titipanNbMinggu);
	}

	public Kebijakan withRisiko(Kehatian value) {
		return new Kebijakan(mode, value, uangSakuPersen, izin, larang, jatahLumbung, tungguTerobosanHari, tungguDaoHari,
				jenjang, hatiHatiTerobosan, beritahu, seteru, titipanNbMinggu);
	}

	public Kebijakan withIzin(int value) {
		return new Kebijakan(mode, risiko, uangSakuPersen, value, larang, jatahLumbung, tungguTerobosanHari, tungguDaoHari,
				jenjang, hatiHatiTerobosan, beritahu, seteru, titipanNbMinggu);
	}

	public Kebijakan withUangSaku(int persen) {
		return new Kebijakan(mode, risiko, persen, izin, larang, jatahLumbung, tungguTerobosanHari, tungguDaoHari,
				jenjang, hatiHatiTerobosan, beritahu, seteru, titipanNbMinggu);
	}

	public Kebijakan withLarang(List<String> value) {
		return new Kebijakan(mode, risiko, uangSakuPersen, izin, value, jatahLumbung, tungguTerobosanHari, tungguDaoHari,
				jenjang, hatiHatiTerobosan, beritahu, seteru, titipanNbMinggu);
	}

	public Kebijakan withSeteru(SikapSeteru value) {
		return new Kebijakan(mode, risiko, uangSakuPersen, izin, larang, jatahLumbung, tungguTerobosanHari, tungguDaoHari,
				jenjang, hatiHatiTerobosan, beritahu, value, titipanNbMinggu);
	}

	// ================================================================== saved form

	/** The full compound (hall policy). */
	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("mode", mode.id());
		tag.putString("risiko", risiko.id());
		tag.putInt("uangSaku", uangSakuPersen);
		tag.putInt("izin", izin);
		tag.put("larang", strings(larang));
		tag.put("jatah", strings(jatahLumbung));
		tag.putInt("tungguTerobosan", tungguTerobosanHari);
		tag.putInt("tungguDao", tungguDaoHari);
		tag.putString("jenjang", jenjang.name());
		tag.putBoolean("hatiHati", hatiHatiTerobosan);
		tag.putInt("beritahu", beritahu);
		tag.putString("seteru", seteru.name());
		tag.putInt("titipan", titipanNbMinggu);
		return tag;
	}

	/** Reads a full or partial compound: every missing key keeps {@code base}'s value (the per-disciple overlay). */
	public static Kebijakan fromTag(CompoundTag tag, Kebijakan base) {
		if (tag == null || tag.isEmpty()) {
			return base;
		}
		return new Kebijakan(
				ModeMandiri.byId(tag.getStringOr("mode", ""), base.mode),
				Kehatian.byId(tag.getStringOr("risiko", ""), base.risiko),
				tag.getIntOr("uangSaku", base.uangSakuPersen),
				tag.getIntOr("izin", base.izin),
				tag.contains("larang") ? readStrings(tag.getListOrEmpty("larang")) : base.larang,
				tag.contains("jatah") ? readStrings(tag.getListOrEmpty("jatah")) : base.jatahLumbung,
				tag.getIntOr("tungguTerobosan", base.tungguTerobosanHari),
				tag.getIntOr("tungguDao", base.tungguDaoHari),
				enumOr(Jenjang.class, tag.getStringOr("jenjang", ""), base.jenjang),
				tag.getBooleanOr("hatiHati", base.hatiHatiTerobosan),
				tag.getIntOr("beritahu", base.beritahu),
				enumOr(SikapSeteru.class, tag.getStringOr("seteru", ""), base.seteru),
				tag.getIntOr("titipan", base.titipanNbMinggu));
	}

	private static ListTag strings(List<String> values) {
		ListTag list = new ListTag();
		for (String value : values) {
			list.add(StringTag.valueOf(value));
		}
		return list;
	}

	private static List<String> readStrings(ListTag list) {
		List<String> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			String value = list.getStringOr(i, "");
			if (!value.isEmpty()) {
				out.add(value);
			}
		}
		return out;
	}

	private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
		if (name == null || name.isEmpty()) {
			return fallback;
		}
		for (E value : type.getEnumConstants()) {
			if (value.name().equalsIgnoreCase(name)) {
				return value;
			}
		}
		return fallback;
	}
}

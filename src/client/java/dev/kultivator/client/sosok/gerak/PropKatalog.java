package dev.kultivator.client.sosok.gerak;

import dev.kultivator.Kultivator;
import dev.kultivator.sosok.PropId;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The prop catalog of the Gerak system (DESIGN 40.5.7, contract C40.G1): which item model shows a prop index and how a
 * hand, two hands or the back hold it. Indexes 1-40 are DESIGN 38.O.4 ({@code kultivator:fx/tempat/<name>}, 38MD), 41-62
 * DESIGN 38.T.8.1 ({@code kultivator:fx/tempat/tema/<name>}, 38TV), 63-88 DESIGN 40 ({@code kultivator:fx/sosok/<name>},
 * 40K); the names are {@link PropId}'s. 100-149 belong to DESIGN 39 (39E 100-129, 39F 130-149) and 150-199 to later
 * DESIGN 38 work, registered from their client init with {@link #daftar}.
 * <p>
 * A prop is drawn as a lazily made {@code ItemStack(Items.STICK)} carrying {@code DataComponents.ITEM_MODEL} (pattern of
 * {@code PhysiqueVisuals.fx}; never in a static initializer), cached per index. A model whose
 * {@code assets/<ns>/items/<path>.json} is missing is not drawn at all (logged once) instead of showing the purple
 * cube, so a module that has not delivered its models yet only loses the prop.
 * <p>
 * Prop models follow 38.O.0: anchor (8, 8, 8), front to the north (-z), +y up. {@link Pegangan} is the grip in the hand
 * frame after {@code translateToHand} and the fist offset (pixels, degrees, the piece convention of {@code FormModels}:
 * model +y points back up the arm at rx 0, forward out of the fist at rx 90). Client thread only.
 */
public final class PropKatalog {

	/** A long prop held along the arm (brooms, spears, staffs): two-handed clips hold it in the right hand's grip. */
	public static final int PANJANG = 1;
	/** A big prop, still drawn at the KURANG level of detail (40.6.1). */
	public static final int BESAR = 1 << 1;
	/**
	 * The prop keeps its own upright orientation whatever the arm does (the arm's turn is undone at the fist): buckets,
	 * baskets, purses and strings of fish hang straight down, bowls, saucers, trays and palettes stay level, the fly
	 * whisk's tail drapes down. The grip is then read in the body's upright frame (+y of the model up, -z forward).
	 */
	public static final int TEGAK = 1 << 2;

	/**
	 * A grip: offset in model pixels and turn in degrees (x, then y, then z) in the holding frame, a size, and
	 * {@link #PANJANG}/{@link #BESAR} bits.
	 */
	public record Pegangan(float dx, float dy, float dz, float rx, float ry, float rz, float skala, int sifat) {

		public boolean panjang() {
			return (sifat & PANJANG) != 0;
		}

		public boolean besar() {
			return (sifat & BESAR) != 0;
		}

		public boolean tegak() {
			return (sifat & TEGAK) != 0;
		}
	}

	private static final int UKURAN = PropId.TEMPAT_TAMBAHAN_LAST + 1;
	private static final Pegangan TANGAN_BAWAAN = new Pegangan(0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 0.0F, 1.0F, 0);
	private static final Pegangan PUNGGUNG_BAWAAN = new Pegangan(0.0F, 4.0F, 2.7F, 0.0F, 0.0F, 0.0F, 1.0F, 0);
	private static final Pegangan DUA_BAWAAN = new Pegangan(0.0F, 1.0F, -2.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0);

	private static final Identifier[] MODEL = new Identifier[UKURAN];
	private static final ItemStack[] STACK = new ItemStack[UKURAN];
	/** 0 not checked, 1 the model exists, 2 missing. */
	private static final byte[] ADA = new byte[UKURAN];
	private static final Pegangan[] TANGAN = new Pegangan[UKURAN];
	private static final Pegangan[] PUNGGUNG = new Pegangan[UKURAN];
	private static final Pegangan[] DUA = new Pegangan[UKURAN];
	private static final Set<Integer> DIPERINGATKAN = new HashSet<>();

	static {
		// ---------------------------------------------------------------- 38.O.4 hand props
		tangan(PropId.MANGKUK, 0, -0.5F, -1.5F, 0, 0, 0, 0.9F, TEGAK);
		tangan(PropId.SUMPIT, 0, 0, -2, 70, 0, 0, 1.0F, 0);
		tangan(PropId.CANGKIR, 0, 0, -0.5F, 90, 0, 0, 0.9F, 0);
		tangan(PropId.LEPEK, 0, -0.5F, -1.0F, 0, 0, 0, 1.0F, TEGAK);
		tangan(PropId.TEKO, 0, 0, -1, 90, 0, 0, 0.9F, 0);
		tangan(PropId.SENDOK_SAYUR, 0, 0, 0, -125, 0, 0, 1.0F, 0);
		tangan(PropId.SAPU, 0, -8, 0, 180, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.PALU, 0, 0, -3, 90, 0, 0, 1.0F, 0);
		tangan(PropId.ALU, 0, 0, 0, -135, 0, 0, 1.0F, 0);
		tangan(PropId.KUAS, 0, 0, 0, -118, 0, 0, 1.0F, 0);
		tangan(PropId.TONGKAT, 0, -1, 0, 180, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.PETI, 0, 2, -3, 90, 0, 0, 0.8F, BESAR);
		tangan(PropId.PIKULAN, 0, 0, 0, 90, 0, 0, 1.0F, BESAR);
		tangan(PropId.JORAN, 0, 0, -10, 75, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.CANGKUL, 0, -4, 0, 180, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.KERANJANG, 0, 0.4F, 0, 0, 0, 0, 0.8F, BESAR | TEGAK);
		tangan(PropId.EMBER, 0, 0.2F, 0, 0, 0, 0, 0.9F, BESAR | TEGAK);
		tangan(PropId.DUPA, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.PEMUKUL, 0, 0, -3, 90, 0, 0, 1.0F, 0);
		tangan(PropId.PALU_KAYU, 0, 0, -2, 90, 0, 0, 1.0F, 0);
		tangan(PropId.KIPAS_LIPAT, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.PAPAN_CATUR, 0, 0, -2, 90, 0, 0, 1.0F, BESAR);
		tangan(PropId.PAYUNG_KERTAS, 0, -10, 0, 0, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.GASING, 0, 0, -1, 90, 0, 0, 0.8F, 0);
		tangan(PropId.LAYANG_LAYANG, 0, 0, 0, 90, 0, 0, 1.0F, BESAR);
		tangan(PropId.LENTERA_TANGAN, 0, 5, 0, 0, 0, 0, 1.0F, BESAR);
		tangan(PropId.KANTONG_UANG, 0, 1.4F, 0, 0, 0, 0, 0.9F, TEGAK);
		tangan(PropId.NAMPAN, 0, -1, -3, 0, 0, 0, 1.0F, BESAR | TEGAK);
		tangan(PropId.SENDOK_PLESTER, 0, 0, -2, 90, 0, 0, 1.0F, 0);
		tangan(PropId.PAPAN_KAYU, 0, 0, -2, 90, 0, 0, 1.0F, BESAR);
		tangan(PropId.SEKOP, 0, -4, 0, 180, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.TOMBAK, 0, -6, 0, 0, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.TEROPONG, 0, 0, -2, 0, 0, 0, 1.0F, 0);
		tangan(PropId.LABU_PIL, 0, 0, -0.5F, 90, 0, 0, 0.9F, 0);
		tangan(PropId.PAHAT, 0, 0, -1, 120, 0, 0, 1.0F, 0);
		// ---------------------------------------------------------------- 38.T.8.1 themed props
		tangan(PropId.DAYUNG_YULOH, 0, -5, 10.8F, -65, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.JALA, 0, 3, -2, 90, 0, 0, 1.0F, BESAR);
		tangan(PropId.IKAN_TALI, 0, 0.5F, 0, 0, 0, 0, 1.0F, TEGAK);
		tangan(PropId.TALI, 0, 0, 0, 0, 0, 0, 1.0F, TEGAK);
		tangan(PropId.PALU_DEMPUL, 0, 0, -2, 90, 0, 0, 1.0F, 0);
		tangan(PropId.KUAS_BESAR, 0, 0, -3, 120, 0, 0, 1.0F, 0);
		tangan(PropId.KENDI, 0, 3, 0, 0, 0, 0, 0.9F, 0);
		tangan(PropId.KERANJANG_TEH, 0, 0.6F, 0, 0, 0, 0, 0.8F, BESAR | TEGAK);
		tangan(PropId.LENTERA_TERATAI, 0, 0, -2, 90, 0, 0, 1.0F, 0);
		tangan(PropId.PEMUKUL_KECIL, 0, 0, -2, 90, 0, 0, 1.0F, 0);
		tangan(PropId.TONGKAT_CINCIN, 0, 4, 0, 0, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.TONGKAT_BAMBU, 0, 4, 0, 0, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.RANGKA_PUNGGUNG, 0, 2, -3, 90, 0, 0, 0.8F, BESAR);
		tangan(PropId.TAS_DUPA, 0, 3.0F, 0, 0, 0, 0, 0.9F, TEGAK);
		tangan(PropId.CAMBUK_KAFILAH, 0, 0, -2, 70, 0, 0, 1.0F, 0);
		tangan(PropId.SABIT_HERBA, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.KIPAS_SUTRA, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.PALET, 0, -0.5F, -2, 0, 0, 0, 1.0F, TEGAK);
		// ---------------------------------------------------------------- 40.5.7 Sosok props
		tangan(PropId.KERTAS_JIMAT, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.KIPAS_TUNGKU, 0, 0, -2, 90, 0, 0, 1.0F, 0);
		tangan(PropId.KEBUT, 0, 1, -1, 180, 0, 12, 0.7F, TEGAK);
		tangan(PropId.PAPAN_TITAH, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.PAPAN_CATATAN, 0, 0, -1, 70, 0, 0, 1.0F, 0);
		tangan(PropId.GULUNGAN, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.CAWAN_ARAK, 0, 0, -0.5F, 90, 0, 0, 1.0F, 0);
		tangan(PropId.LABU_ARAK, 0, 0, 0, 0, 0, 0, 0.9F, 0);
		tangan(PropId.PIL, 0, 0, -0.5F, 90, 0, 0, 0.8F, 0);
		tangan(PropId.SERULING, 0, -3, 0, 180, 0, 0, 1.0F, 0);
		tangan(PropId.GUQIN, 0, 0, -3, 90, 0, 0, 1.0F, BESAR);
		tangan(PropId.PEDANG_KAYU, 0, 0, -1, 90, 0, 0, 0.9F, 0);
		tangan(PropId.BENDERA_FORMASI, 0, -2, 0, 180, 0, 0, 1.0F, PANJANG | BESAR);
		tangan(PropId.CERMIN_PERUNGGU, 0, 0, -1, 90, 0, 0, 1.0F, 0);
		tangan(PropId.TASBIH_TANGAN, 0, 5.0F, 0, 0, 0, 0, 0.9F, TEGAK);
		tangan(PropId.TALI_KEKANG, 0, 1.4F, 0, 0, 0, 0, 1.0F, TEGAK);
		tangan(PropId.KUNCI_KAMAR, 0, 0.2F, 0, 0, 0, 0, 1.0F, TEGAK);
		tangan(PropId.KOTAK_OBAT, 0, 1.2F, 0, 0, 0, 0, 0.9F, TEGAK);
		tangan(PropId.TROFI_TARING, 0, 0.3F, 0, 0, 0, 0, 1.0F, TEGAK);

		// ---------------------------------------------------------------- the back (body frame, from the shoulders)
		punggung(PropId.PIKULAN, -4.5F, -1.2F, 0, 0, 0, 0, 1.0F);
		punggung(PropId.PETI, 0, 5, 4.5F, 0, 0, 0, 0.8F);
		punggung(PropId.KERANJANG, 0, 5, 4.5F, 0, 0, 0, 0.9F);
		punggung(PropId.KERANJANG_TEH, 0, 5, 4.5F, 0, 0, 0, 0.9F);
		punggung(PropId.RANGKA_PUNGGUNG, 0, 5, 4, 0, 0, 0, 1.0F);
		punggung(PropId.TOMBAK, 0, 5, 3.2F, 0, 0, 35, 1.0F);
		punggung(PropId.PEDANG_KAYU, 0, 5, 3.2F, 0, 0, 35, 0.9F);
		punggung(PropId.CANGKUL, 0, 5, 3.2F, 0, 0, -30, 1.0F);
		punggung(PropId.SEKOP, 0, 5, 3.2F, 0, 0, -30, 1.0F);
		punggung(PropId.PAYUNG_KERTAS, 0, 5, 3.2F, 0, 0, 25, 0.9F);
		punggung(PropId.JALA, 0, 4, 4, 0, 0, 0, 0.9F);
		punggung(PropId.GUQIN, 0, 5, 3.5F, 0, 0, 30, 0.9F);
		punggung(PropId.GULUNGAN, 0, 3, 3, 0, 0, 40, 1.0F);

		// ---------------------------------------------------------------- two hands (between the fists, body yaw)
		dua(PropId.KITAB, 0, 1, -2, -25, 0, 0, 1.0F);
		dua(PropId.SEMPOA, 0, 1, -2.5F, 50, 0, 0, 1.0F);
		dua(PropId.PETI, 0, 2.5F, -4, 0, 0, 0, 0.9F);
		dua(PropId.KAIN, 0, 2, -2, 0, 0, 0, 1.0F);
		dua(PropId.DUPA, 0, -1, -2, 0, 0, 0, 1.0F);
		dua(PropId.PIL, 0, 0, -1.5F, 0, 0, 0, 0.8F);
		dua(PropId.BATU_ROH, 0, -0.5F, -2, 0, 0, 0, 1.0F);
		dua(PropId.LENTERA_TERATAI, 0, 0, -3, 0, 0, 0, 1.0F);
		dua(PropId.JALA, 0, 3, -3, 0, 0, 0, 1.0F);
		dua(PropId.TEROPONG, 0, 0, 4.5F, 0, 0, 0, 1.0F);
		dua(PropId.NAMPAN, 0, 0.5F, -3, 0, 0, 0, 1.0F);
		dua(PropId.PAPAN_KAYU, 0, 2, -3, 0, 90, 0, 1.0F);
	}

	// ================================================================== registration (C40.G1)

	/**
	 * Registers a prop of another module: ids 100-149 for DESIGN 39 (39E 100-129, 39F 130-149) and 150-199 for later
	 * DESIGN 38. {@code modelItem} is the item model id ({@code assets/<ns>/items/<path>.json}). The default grip holds it
	 * forward out of the fist; use {@link #daftar(int, Identifier, Pegangan)} for another one.
	 */
	public static void daftar(int id, Identifier modelItem) {
		daftar(id, modelItem, null);
	}

	/** {@link #daftar(int, Identifier)} with the hand grip (also used on two hands and the back). */
	public static void daftar(int id, Identifier modelItem, @Nullable Pegangan pegangan) {
		if (id < PropId.MANDIRI_FIRST || id > PropId.TEMPAT_TAMBAHAN_LAST) {
			throw new IllegalArgumentException("Gerak prop id outside 100-199: " + id);
		}
		MODEL[id] = modelItem;
		STACK[id] = null;
		ADA[id] = 0;
		if (pegangan != null) {
			TANGAN[id] = pegangan;
		}
	}

	// ================================================================== lookup

	/** The item model id of a prop, or null (unknown index). */
	public static @Nullable Identifier model(int id) {
		if (id <= 0 || id >= UKURAN) {
			return null;
		}
		if (MODEL[id] == null && PropId.ada(id)) {
			String jalur = PropId.jalurModel(id);
			if (jalur != null) {
				MODEL[id] = Kultivator.id(jalur);
			}
		}
		return MODEL[id];
	}

	/** True when the prop can be drawn (known index and its item model file exists; checked once). */
	public static boolean ada(int id) {
		if (id <= 0 || id >= UKURAN) {
			return false;
		}
		if (ADA[id] == 0) {
			Identifier m = model(id);
			boolean ok = m != null && berkasAda(m);
			ADA[id] = (byte) (ok ? 1 : 2);
			if (!ok && DIPERINGATKAN.add(id)) {
				Kultivator.LOGGER.warn("[Sosok] prop {} ({}) has no item model {}, not drawn", id, PropId.nama(id), m);
			}
		}
		return ADA[id] == 1;
	}

	/** The stack that shows a prop (made on first use, then cached), or null when it cannot be drawn. */
	public static @Nullable ItemStack stack(int id) {
		if (!ada(id)) {
			return null;
		}
		ItemStack s = STACK[id];
		if (s == null) {
			s = new ItemStack(Items.STICK);
			s.set(DataComponents.ITEM_MODEL, MODEL[id]);
			STACK[id] = s;
		}
		return s;
	}

	/** The grip in one hand. */
	public static Pegangan tangan(int id) {
		Pegangan p = id > 0 && id < UKURAN ? TANGAN[id] : null;
		return p != null ? p : TANGAN_BAWAAN;
	}

	/** Where the prop sits on the back (body frame, pixels from the neck). */
	public static Pegangan punggung(int id) {
		Pegangan p = id > 0 && id < UKURAN ? PUNGGUNG[id] : null;
		return p != null ? p : PUNGGUNG_BAWAAN;
	}

	/** Where a flat prop sits between two hands (offset from the fists' midpoint, body yaw). */
	public static Pegangan duaTangan(int id) {
		Pegangan p = id > 0 && id < UKURAN ? DUA[id] : null;
		return p != null ? p : DUA_BAWAAN;
	}

	/** True for a big prop (still drawn at the KURANG level). */
	public static boolean besar(int id) {
		return tangan(id).besar();
	}

	/** Forgets which model files exist (resource reload, {@code /sosok muat_ulang}, world change). */
	public static void lupakan() {
		java.util.Arrays.fill(ADA, (byte) 0);
		java.util.Arrays.fill(STACK, null);
		DIPERINGATKAN.clear();
	}

	private static boolean berkasAda(Identifier model) {
		try {
			Identifier berkas = Identifier.fromNamespaceAndPath(model.getNamespace(), "items/" + model.getPath() + ".json");
			return Minecraft.getInstance().getResourceManager().getResource(berkas).isPresent();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static void tangan(int id, float dx, float dy, float dz, float rx, float ry, float rz, float skala, int sifat) {
		TANGAN[id] = new Pegangan(dx, dy, dz, rx, ry, rz, skala, sifat);
	}

	private static void punggung(int id, float dx, float dy, float dz, float rx, float ry, float rz, float skala) {
		PUNGGUNG[id] = new Pegangan(dx, dy, dz, rx, ry, rz, skala, 0);
	}

	private static void dua(int id, float dx, float dy, float dz, float rx, float ry, float rz, float skala) {
		DUA[id] = new Pegangan(dx, dy, dz, rx, ry, rz, skala, 0);
	}

	private PropKatalog() {
	}
}

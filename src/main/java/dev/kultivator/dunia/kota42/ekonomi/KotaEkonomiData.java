package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaBagianData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * SavedData {@code kultivator:kota_ekonomi} in the overworld (DESIGN 42.13.1), owner 42C (+ 42C-2). Skeleton of 42A0 with the
 * {@link KotaBagianData#bagian(String) bagian} split of {@code jadwal-41-42.md} matrix row 14: every module of the
 * owner group keeps its state in its own section and calls {@link #ubah()}; nobody edits this class for a new field.
 * Sections: houses ({@code rumah}: situs -> id -> owner, kind, rent until, seal until, arrears), player shops ({@code toko}), resident businesses and shares ({@code usaha}), warehouses ({@code gudang}, {@code ItemStack} codec, at most 108 per player and place), trade contracts and trade names ({@code kontrak}), tax rate and 8-week history ({@code pajak}), offices and cases ({@code jabatan}, {@code perkara}), town stock of the 12 categories and player saturation ({@code stok}), Ghost Market stolen goods ({@code pasar_hantu}).
 */
public final class KotaEkonomiData extends KotaBagianData {

	public static final Codec<KotaEkonomiData> CODEC = CompoundTag.CODEC.xmap(tag -> baca(new KotaEkonomiData(), tag), KotaEkonomiData::tulis);
	public static final SavedDataType<KotaEkonomiData> TYPE = new SavedDataType<>(Kultivator.id("kota_ekonomi"), KotaEkonomiData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Sub-module key {@code "rumah"}. */
	public static final String RUMAH = "rumah";
	/** Sub-module key {@code "toko"}. */
	public static final String TOKO = "toko";
	/** Sub-module key {@code "usaha"}. */
	public static final String USAHA = "usaha";
	/** Sub-module key {@code "gudang"}. */
	public static final String GUDANG = "gudang";
	/** Sub-module key {@code "kontrak"}. */
	public static final String KONTRAK = "kontrak";
	/** Sub-module key {@code "pajak"}. */
	public static final String PAJAK = "pajak";
	/** Sub-module key {@code "jabatan"}. */
	public static final String JABATAN = "jabatan";
	/** Sub-module key {@code "perkara"}. */
	public static final String PERKARA = "perkara";
	/** Sub-module key {@code "stok"}. */
	public static final String STOK = "stok";
	/** Sub-module key {@code "pasar_hantu"}. */
	public static final String PASAR_HANTU = "pasar_hantu";
	/** Sub-module key {@code "kas"}. */
	public static final String KAS = "kas";

	public KotaEkonomiData() {
	}

	/** The world's data (overworld storage), created on first use. */
	public static KotaEkonomiData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	@Override
	protected CompoundTag tulis() {
		return super.tulis();
	}
}

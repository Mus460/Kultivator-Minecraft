package dev.kultivator.dunia.kota42.tumbuh;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaBagianData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * SavedData {@code kultivator:kota_tumbuh} in the overworld (DESIGN 42.13.1), owner 42F. Skeleton of 42A0 with the
 * {@link KotaBagianData#bagian(String) bagian} split of {@code jadwal-41-42.md} matrix row 14: every module of the
 * owner group keeps its state in its own section and calls {@link #ubah()}; nobody edits this class for a new field.
 * Sections: plots ({@code kavling}: situs -> id, pos, facing, style, kind, stage, material used, donors, blocked), material stock of the five kinds ({@code bahan}), prosperity ({@code makmur}) and rebuild projects ({@code bangun}: cluster, refugees, donors, memorial).
 */
public final class KotaTumbuhData extends KotaBagianData {

	public static final Codec<KotaTumbuhData> CODEC = CompoundTag.CODEC.xmap(tag -> baca(new KotaTumbuhData(), tag), KotaTumbuhData::tulis);
	public static final SavedDataType<KotaTumbuhData> TYPE = new SavedDataType<>(Kultivator.id("kota_tumbuh"), KotaTumbuhData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Sub-module key {@code "kavling"}. */
	public static final String KAVLING = "kavling";
	/** Sub-module key {@code "bahan"}. */
	public static final String BAHAN = "bahan";
	/** Sub-module key {@code "makmur"}. */
	public static final String MAKMUR = "makmur";
	/** Sub-module key {@code "bangun"}. */
	public static final String BANGUN = "bangun";
	/** Sub-module key {@code "blokade"}: blockade days and the war refugees' houses of a place. */
	public static final String BLOKADE = "blokade";

	public KotaTumbuhData() {
	}

	/** The world's data (overworld storage), created on first use. */
	public static KotaTumbuhData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	@Override
	protected CompoundTag tulis() {
		return super.tulis();
	}
}

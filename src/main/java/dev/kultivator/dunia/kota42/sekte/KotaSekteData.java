package dev.kultivator.dunia.kota42.sekte;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaBagianData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * SavedData {@code kultivator:kota_sekte} in the overworld (DESIGN 42.13.1), owner 42E. Skeleton of 42A0 with the
 * {@link KotaBagianData#bagian(String) bagian} split of {@code jadwal-41-42.md} matrix row 14: every module of the
 * owner group keeps its state in its own section and calls {@link #ubah()}; nobody edits this class for a new field.
 * Sections: recruitment offices ({@code kantor}: situs -> owner, rent, guard, at most 12 applicants), protection pacts ({@code pakta}: owner, start, failures, tribute, next raid), branches ({@code cabang}: owner, house, kind, since) and disciple town tasks ({@code murid}: soul -> place, task, until).
 */
public final class KotaSekteData extends KotaBagianData {

	public static final Codec<KotaSekteData> CODEC = CompoundTag.CODEC.xmap(tag -> baca(new KotaSekteData(), tag), KotaSekteData::tulis);
	public static final SavedDataType<KotaSekteData> TYPE = new SavedDataType<>(Kultivator.id("kota_sekte"), KotaSekteData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Sub-module key {@code "kantor"}. */
	public static final String KANTOR = "kantor";
	/** Sub-module key {@code "pakta"}. */
	public static final String PAKTA = "pakta";
	/** Sub-module key {@code "cabang"}. */
	public static final String CABANG = "cabang";
	/** Sub-module key {@code "murid"}. */
	public static final String MURID = "murid";
	/** Sub-module key {@code "restu"}: player uuid -> id of the resident the office sent to the "Restu Ayah" quest. */
	public static final String RESTU = "restu";

	public KotaSekteData() {
	}

	/** The world's data (overworld storage), created on first use. */
	public static KotaSekteData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	@Override
	protected CompoundTag tulis() {
		return super.tulis();
	}
}

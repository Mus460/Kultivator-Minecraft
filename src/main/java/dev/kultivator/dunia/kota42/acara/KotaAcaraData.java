package dev.kultivator.dunia.kota42.acara;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaBagianData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * SavedData {@code kultivator:kota_acara} in the overworld (DESIGN 42.13.1), owner 42D (+ 42D-P, 42D-H). Skeleton of 42A0 with the
 * {@link KotaBagianData#bagian(String) bagian} split of {@code jadwal-41-42.md} matrix row 14: every module of the
 * owner group keeps its state in its own section and calls {@link #ubah()}; nobody edits this class for a new field.
 * Sections: the calendar per place ({@code kalender}: scheduled ceremonies with kind, residents, hour), the six troupes ({@code rombongan}: route position, next town), street-event cooldowns ({@code peristiwa}), festivals already run ({@code musim}), the night district ({@code malam}) and town animals ({@code hewan}).
 */
public final class KotaAcaraData extends KotaBagianData {

	public static final Codec<KotaAcaraData> CODEC = CompoundTag.CODEC.xmap(tag -> baca(new KotaAcaraData(), tag), KotaAcaraData::tulis);
	public static final SavedDataType<KotaAcaraData> TYPE = new SavedDataType<>(Kultivator.id("kota_acara"), KotaAcaraData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Sub-module key {@code "kalender"}. */
	public static final String KALENDER = "kalender";
	/** Sub-module key {@code "rombongan"}. */
	public static final String ROMBONGAN = "rombongan";
	/** Sub-module key {@code "peristiwa"}. */
	public static final String PERISTIWA = "peristiwa";
	/** Sub-module key {@code "musim"}. */
	public static final String MUSIM = "musim";
	/** Sub-module key {@code "malam"}. */
	public static final String MALAM = "malam";
	/** Sub-module key {@code "hewan"}. */
	public static final String HEWAN = "hewan";

	public KotaAcaraData() {
	}

	/** The world's data (overworld storage), created on first use. */
	public static KotaAcaraData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	@Override
	protected CompoundTag tulis() {
		return super.tulis();
	}
}

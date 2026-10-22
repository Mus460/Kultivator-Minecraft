package dev.kultivator.formasi;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Adds module fields to the {@code formasi} part of {@code HallInfo} (DESIGN 24.12), e.g. module B's {@code ext}. */
@FunctionalInterface
public interface InfoPart {
	void write(ServerPlayer viewer, UUID owner, CompoundTag formasi);

	/**
	 * The meditation absorption limit of the viewer (audit M4 T6), as {@code formasi.med}: {@code inner} (site × Qi formation ×
	 * fuel of the current session), {@code cap} ({@link Meditation#MULTIPLIER_CAP}), {@code sect} (the sect factor G applied on
	 * top) and {@code sectCap} ({@link Meditation#GATHER_SECT_CAP}); {@code capped} is true when part of {@code inner} is wasted.
	 * Registered by {@code formasi.integrate.ZoneBridge}; the panel line uses {@code kultivator.kekuatan.formasi.medcap}.
	 */
	static void meditationLimit(ServerPlayer viewer, UUID owner, CompoundTag formasi) {
		CultivationData data = Cultivators.get(viewer);
		CompoundTag med = new CompoundTag();
		double inner = data.meditating() ? Meditation.innerMultiplier(viewer, data) : 0.0D;
		med.putFloat("inner", (float) inner);
		med.putFloat("cap", (float) Meditation.MULTIPLIER_CAP);
		med.putFloat("sect", (float) Meditation.sectFactor(viewer));
		med.putFloat("sectCap", (float) Meditation.GATHER_SECT_CAP);
		med.putBoolean("capped", inner > Meditation.MULTIPLIER_CAP + 1.0E-6D);
		formasi.put("med", med);
	}
}

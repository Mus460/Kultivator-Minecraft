package dev.kultivator.abadi;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

/**
 * SavedData {@code kultivator:alam_abadi} in the overworld (DESIGN 26.13, owned by P9-langit): the arrival island found by
 * {@link ArrivalIsland} ({@code arrival}, absent until the search finished) and the auction bookkeeping
 * ({@code auction {session, seed}}: the last session index that was settled, so a session is paid only once).
 */
public final class AlamAbadiData extends SavedData {

	public static final Codec<AlamAbadiData> CODEC = CompoundTag.CODEC.xmap(AlamAbadiData::fromTag, AlamAbadiData::toTag);
	public static final SavedDataType<AlamAbadiData> TYPE = new SavedDataType<>(Kultivator.id("alam_abadi"), AlamAbadiData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private @Nullable BlockPos arrival;
	/** True when the search found no island and the fallback (0, 120, 0) is used. */
	private boolean fallback;
	private long settledSession = -1L;

	public AlamAbadiData() {
	}

	/** The data of the running server (overworld storage). */
	public static AlamAbadiData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable BlockPos arrival() {
		return arrival;
	}

	public boolean fallback() {
		return fallback;
	}

	void setArrival(@Nullable BlockPos pos, boolean usedFallback) {
		this.arrival = pos == null ? null : pos.immutable();
		this.fallback = usedFallback;
		setDirty();
	}

	long settledSession() {
		return settledSession;
	}

	void setSettledSession(long session) {
		this.settledSession = session;
		setDirty();
	}

	private static AlamAbadiData fromTag(CompoundTag tag) {
		AlamAbadiData data = new AlamAbadiData();
		CompoundTag arrival = tag.getCompoundOrEmpty("arrival");
		if (!arrival.isEmpty()) {
			data.arrival = new BlockPos(arrival.getIntOr("x", 0), arrival.getIntOr("y", 120), arrival.getIntOr("z", 0));
			data.fallback = arrival.getBooleanOr("fallback", false);
		}
		data.settledSession = tag.getCompoundOrEmpty("auction").getLongOr("session", -1L);
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		if (arrival != null) {
			CompoundTag pos = new CompoundTag();
			pos.putInt("x", arrival.getX());
			pos.putInt("y", arrival.getY());
			pos.putInt("z", arrival.getZ());
			pos.putBoolean("fallback", fallback);
			tag.put("arrival", pos);
		}
		CompoundTag auction = new CompoundTag();
		auction.putLong("session", settledSession);
		tag.put("auction", auction);
		return tag;
	}
}

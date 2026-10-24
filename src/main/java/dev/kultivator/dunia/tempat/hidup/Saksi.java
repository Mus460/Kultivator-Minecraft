package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Witnesses without bodies (DESIGN 38.N.6 "saksi data", 38.B.5.2): a crime in a lot the schedule says is occupied
 * right now is seen with a 50 % chance, even when no body stands there (the shopkeeper behind the counter, the family
 * in the house). Positions come from the residents' cached goals of the current cycle ({@link Pemanggil#posisi});
 * nothing is spawned or loaded. The draw is seeded by place, culprit and minute so asking twice gives one answer.
 */
public final class Saksi {

	/** "Same lot": within this many blocks of the crime, three blocks up or down. */
	public static final int LOT = 12;

	/** True when a resident whose scheduled goal lies in the crime's lot saw {@code pelaku} (50 %). */
	public static boolean dataSaksi(ServerLevel level, BlockPos pos, ServerPlayer pelaku) {
		TempatHuni huni = tempat(level, pos);
		if (huni == null) {
			return false;
		}
		int ada = penghuniLot(huni, pos);
		if (ada == 0) {
			return false;
		}
		long minute = level.getGameTime() / 1200L;
		long h = PendudukGen.mixSeed(huni.situsId ^ pelaku.getUUID().getLeastSignificantBits() ^ minute * 0x9E3779B97F4A7C15L ^ pos.asLong());
		// one occupant: 50 %; every further occupant halves the chance of nobody noticing
		double nobody = Math.pow(0.5D, Math.min(ada, 6));
		return (h >>> 11) * 0x1.0p-53 >= nobody;
	}

	/** How many residents (no body) are scheduled in the lot around {@code pos} now. */
	public static int penghuniLot(TempatHuni huni, BlockPos pos) {
		int n = 0;
		for (Penduduk p : huni.penduduk()) {
			if (huni.tubuh.containsKey(p.id())) {
				continue;
			}
			TempatHuni.Tujuan t = Pemanggil.posisi(huni.situsId, p.id());
			if (t == null) {
				continue;
			}
			BlockPos at = t.pos();
			if (Math.abs(at.getY() - pos.getY()) <= 3 && Math.abs(at.getX() - pos.getX()) <= LOT && Math.abs(at.getZ() - pos.getZ()) <= LOT) {
				n++;
			}
		}
		return n;
	}

	/** The active inhabited place at {@code pos}, or null. */
	static @Nullable TempatHuni tempat(ServerLevel level, BlockPos pos) {
		TempatInfo info = TempatApi.tempatDi(level, pos);
		if (info != null) {
			TempatHuni huni = Pemanggil.huni(info.situsId());
			if (huni != null) {
				return huni;
			}
		}
		for (TempatHuni huni : Pemanggil.semua()) {
			if (huni.dimensi.equals(level.dimension()) && huni.kotak.isInside(pos)) {
				return huni;
			}
		}
		return null;
	}

	private Saksi() {
	}
}

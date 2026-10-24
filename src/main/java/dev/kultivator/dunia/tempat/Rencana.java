package dev.kultivator.dunia.tempat;

import dev.kultivator.dunia.tempat.jalan.SimpulJalan;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A deterministic place plan (DESIGN 38.B.2.2, contract C38.W2 + 38.T.2.1): which chunks start which district of a
 * multi-start place, from the world seed alone. Registered once by its module in {@link RencanaRegistry}
 * ({@code kota_besar} 38W, {@code kompleks_sekte} 38DS, {@code kota_tema_<tema>} 38TK, {@code jalan_titik} 38W).
 * Implementations must be pure, thread-safe (worldgen threads) and cheap per call (cache the plan per seed).
 */
public interface Rencana {

	/** True when chunk {@code (cx, cz)} is the start chunk of district {@code distrik} of a candidate (placement). */
	boolean isStart(long seed, int cx, int cz, String distrik);

	/** The district whose start chunk is {@code (cx, cz)}, or null. */
	@Nullable Distrik distrikDi(long seed, int cx, int cz);

	/** Keep-out boxes of every candidate that is planned (38.D.4; XZ boxes, y spans the world). */
	List<BoundingBox> zona(long seed);

	/** Road nodes of the candidates whose centre lies in road cell {@code (selX, selZ)} (2048-block cells). */
	default List<SimpulJalan> simpul(long seed, int selX, int selZ) {
		return List.of();
	}

	/** Safe-ground boxes ({@code DuniaApi.safeGround}); Kota Iblis answers only its Zona Perjanjian (38.T.7.8). */
	default List<BoundingBox> zonaAman(long seed) {
		return zona(seed);
	}

	/**
	 * Every planned district (all candidates, before the terrain verdict), so a {@code rencana_tempat} placement can
	 * answer {@code /locate} per grid cell. Default: none.
	 */
	default List<Distrik> semuaDistrik(long seed) {
		return List.of();
	}

	/** Every candidate of the plan, in evaluation order ({@code uji tempat rencana}). */
	default List<Kandidat> kandidat(long seed) {
		return List.of();
	}

	/**
	 * The shared terrain verdict of a district's place. Worldgen passes {@code PutusanTanah.Sumber.of(context)}, the
	 * server {@code PutusanTanah.Sumber.of(overworld)}; both give the same answer. Default: never suitable.
	 */
	default Putusan putusan(PutusanTanah.Sumber sumber, Distrik distrik) {
		return Putusan.TIDAK;
	}

	/**
	 * The keep-out and safe boxes of every planned district WITH its district (wave W1, additive): lets the keep-out
	 * mixin ({@link TempatKeepOut}) and the runtime zones ({@link TempatZones}) ask the terrain verdict, so a candidate
	 * that is never born protects nothing. A plan that answers the empty list (default) keeps the plain
	 * {@link #zona}/{@link #zonaAman} boxes without a verdict.
	 */
	default List<ZonaRencana> zonaDistrik(long seed) {
		return List.of();
	}

	/**
	 * True when road node {@code simpul} (one of this plan's {@link #simpul}) really exists: the place behind it passes
	 * its terrain verdict. Road edges are born only when both ends are (38.C.6.1). Default: always.
	 */
	default boolean simpulSah(PutusanTanah.Sumber sumber, SimpulJalan simpul) {
		return true;
	}
}

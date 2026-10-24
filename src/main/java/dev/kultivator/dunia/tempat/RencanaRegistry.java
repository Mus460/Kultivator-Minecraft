package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.jalan.SimpulJalan;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of place plans (DESIGN 38.B.2.3, contract C38.W2). {@code kultivator:rencana_tempat} placements and
 * {@code kultivator:tata_letak} structures look their plan up by the JSON field {@code rencana}. An unknown id answers
 * {@link #KOSONG} (never a start, no zones), so a data pack naming a plan whose module is off generates nothing rather
 * than failing. Registration happens in mod init (before any world loads); reads are lock-free afterwards.
 */
public final class RencanaRegistry {

	/** The empty plan: no candidate, no start, no zone. */
	public static final Rencana KOSONG = new Rencana() {
		@Override
		public boolean isStart(long seed, int cx, int cz, String distrik) {
			return false;
		}

		@Override
		public @Nullable Distrik distrikDi(long seed, int cx, int cz) {
			return null;
		}

		@Override
		public List<BoundingBox> zona(long seed) {
			return List.of();
		}
	};

	private static volatile Map<String, Rencana> plans = Map.of();

	/** Registers plan {@code id} (once; a second registration replaces the first with a warning). */
	public static synchronized void register(String id, Rencana rencana) {
		Map<String, Rencana> next = new LinkedHashMap<>(plans);
		if (next.put(id, rencana) != null) {
			Kultivator.LOGGER.warn("Tempat plan {} registered twice; the later one wins", id);
		}
		plans = Collections.unmodifiableMap(next);
	}

	/** Plan {@code id}, or {@link #KOSONG}. */
	public static Rencana get(String id) {
		return plans.getOrDefault(id, KOSONG);
	}

	public static boolean has(String id) {
		return plans.containsKey(id);
	}

	/** Every registered plan id, in registration order. */
	public static Collection<String> ids() {
		return plans.keySet();
	}

	/** Keep-out boxes of every plan for {@code seed} (38.D.4). */
	public static List<BoundingBox> semuaZona(long seed) {
		List<BoundingBox> out = new ArrayList<>();
		for (Rencana r : plans.values()) {
			out.addAll(r.zona(seed));
		}
		return out;
	}

	/** Safe-ground boxes of every plan for {@code seed} (38.0 rule 11). */
	public static List<BoundingBox> semuaZonaAman(long seed) {
		List<BoundingBox> out = new ArrayList<>();
		for (Rencana r : plans.values()) {
			out.addAll(r.zonaAman(seed));
		}
		return out;
	}

	/** Road nodes of every plan in road cell {@code (selX, selZ)} (38.T.2.1, used by {@code RencanaJalan}). */
	public static List<SimpulJalan> semuaSimpul(long seed, int selX, int selZ) {
		List<SimpulJalan> out = new ArrayList<>();
		for (Rencana r : plans.values()) {
			out.addAll(r.simpul(seed, selX, selZ));
		}
		return out;
	}

	private RencanaRegistry() {
	}
}

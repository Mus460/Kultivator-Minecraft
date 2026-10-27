package dev.kultivator.sekte.gabung;

import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The uniform of an NPC sect's people follows the sect's style (DESIGN 41.11.5, D-41V). Every caller dresses a sect body
 * with the plain key ({@code tetua_sekte|sekte=npc:s3}, {@code ketua_sekte|...}, {@code murid.2|...}); this rewrites the
 * key on its way through {@link SosokApi#setRupa} into the style row 41V-M's generator made for it
 * ({@code tetua_sekte.pedang}, {@code ketua_sekte.alkimia}, {@code murid.2.buddha}: sword belt, rosary, furnace apron,
 * hood...). Only NPC sects of this world's plan are rewritten; the keys of everything else pass unchanged.
 */
public final class GabungRupa {

	/** Style of sect id, per world seed; the plan never changes within a world. */
	private static final Map<String, Corak> CORAK = new ConcurrentHashMap<>();
	private static volatile long benih = Long.MIN_VALUE;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		SosokApi.tambahPenyaringRupa(GabungRupa::sesuaikan);
	}

	/** {@code kunci} rewritten to its style row, or {@code kunci} itself. */
	static RupaKunci sesuaikan(RupaKunci kunci) {
		String sekte = kunci.sekte();
		if (sekte == null || !sekte.startsWith("npc:") || !jenisBisa(kunci.kunci())) {
			return kunci;
		}
		Corak corak = corak(sekte.substring(4));
		if (corak == null) {
			return kunci;
		}
		return new RupaKunci(kunci.kunci() + "." + corak.id(), sekte, kunci.dao(), kunci.pangkat());
	}

	/** The keys that have style rows: the elder, the master and the disciples of rank 0-5 (a styled key is not one). */
	private static boolean jenisBisa(String kunci) {
		if (kunci.equals("tetua_sekte") || kunci.equals("ketua_sekte")) {
			return true;
		}
		return kunci.length() == 7 && kunci.startsWith("murid.") && kunci.charAt(6) >= '0' && kunci.charAt(6) <= '5';
	}

	private static @Nullable Corak corak(String sekteId) {
		MinecraftServer server = Gabung.server();
		if (server == null || GabungApi.entry(server, sekteId) == null) {
			return null;
		}
		long seed = server.overworld().getSeed();
		if (seed != benih) {
			CORAK.clear();
			benih = seed;
		}
		return CORAK.computeIfAbsent(sekteId, GabungApi::corak);
	}

	private GabungRupa() {
	}
}

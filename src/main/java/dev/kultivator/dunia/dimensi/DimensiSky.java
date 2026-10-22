package dev.kultivator.dunia.dimensi;

import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * What module D's two worlds feel of module L's sky events (DESIGN 34.4.3 "SKY_EVENT ... D (Dunia Bawah gates open wider
 * during the blood eclipse)"). The events run in the overworld's sky; the realms below and beyond react to them:
 * <ul>
 * <li>{@link SkyKind#BLOOD_ECLIPSE}: the ghost gates stand open (no incense at the altars), Dunia Bawah's hungry ghosts
 * gather half again as thick, the officials speak of it and the souls' view takes a faint crimson edge.</li>
 * <li>{@link SkyKind#TWIN_MOONS}: the spirit tide of Laut Tanpa Batas: every herb bed gives one herb more.</li>
 * <li>{@link SkyKind#METEOR_SHOWER}: falling stars over the sea; the islanders talk of it (flavour only).</li>
 * </ul>
 * Read-only: nothing here starts, stops or changes a sky event, the weather or the time. {@code uji bawah langit} sets a
 * short local test override (this module only; module L's sky is not touched).
 */
public final class DimensiSky {

	private static @Nullable SkyKind override;
	private static long overrideUntil;

	/** The sky event over the overworld right now (the test override first), or null. */
	public static @Nullable SkyKind active(MinecraftServer server) {
		if (override != null) {
			if (DimensiData.now(server) < overrideUntil) {
				return override;
			}
			override = null;
		}
		try {
			return DuniaApi.sky().active(server.overworld());
		} catch (RuntimeException exception) {
			return null;
		}
	}

	public static boolean eclipse(MinecraftServer server) {
		return active(server) == SkyKind.BLOOD_ECLIPSE;
	}

	public static boolean twinMoons(MinecraftServer server) {
		return active(server) == SkyKind.TWIN_MOONS;
	}

	/** 0 none, 1 meteor shower, 2 blood eclipse, 3 twin moons (the client state's {@code sky}). */
	public static int code(MinecraftServer server) {
		SkyKind kind = active(server);
		return kind == null ? 0 : kind.ordinal() + 1;
	}

	/** {@code uji bawah langit <jenis> [menit]}: module D's own reaction test (null or 0 minutes clears it). */
	public static void testOverride(MinecraftServer server, @Nullable SkyKind kind, int minutes) {
		if (kind == null || minutes <= 0) {
			override = null;
			overrideUntil = 0L;
			return;
		}
		override = kind;
		overrideUntil = DimensiData.now(server) + 20L * 60L * minutes;
	}

	static void clear() {
		override = null;
		overrideUntil = 0L;
	}

	private DimensiSky() {
	}
}

package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.SpiritSites;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The themed towns' one meditation-site provider (DESIGN 38.T.11 row Meditasi, {@code SpiritSites.addSiteProvider}): a
 * player meditating inside one district of a themed town gets its extra site, multiplied with the others under
 * {@code SpiritSites.CAP}. Sites served here:
 * <ul>
 * <li>{@code taman_klasik} "Taman Klasik" x1.2: Kota Air, district {@code taman_sastra} (the classical garden).</li>
 * <li>{@code aula_emas} "Aula Emas" x1.4: Kota Gunung, district {@code puncak_emas}, only with the Restu Puncak of the
 * pilgrimage (the gunung agent registers the condition with {@link #daftarSyarat}; none registered = closed).</li>
 * <li>{@code kolam_semedi_darah} "Kolam Semedi Darah" x1.3: Kota Iblis, district {@code menara_terlarang} (the pool lot), only while the
 * player's Infami is at least 20.</li>
 * </ul>
 * "Mata Gurun" (oasis, x1.25) is served by the oasis package itself ({@code oasis.KuilAir}), not here. The test is a
 * plan lookup (no block reads): the player's column must lie within {@value #JARI} blocks of the district centre and
 * within {@value #TINGGI} blocks of its street level. Theme agents may add conditions per theme with
 * {@link #daftarSyarat}; all registered conditions of a site must hold.
 */
public final class TemaSitus {

	/** Horizontal radius around the district centre (inside the 96 cell). */
	public static final int JARI = 40;
	/** Vertical tolerance around the district street level. */
	public static final int TINGGI = 24;
	/** Infami needed for the Kolam Semedi Darah. */
	public static final int INFAMI_KOLAM = 20;

	/**
	 * One extra site.
	 *
	 * @param id        site id (lang {@code kultivator.meditation.site.tema.<id>})
	 * @param tema      the theme
	 * @param distrik   the district id
	 * @param kali      the multiplier
	 * @param tertutup  true: closed while no condition is registered for the theme (the Aula Emas)
	 */
	public record Situs(String id, Tema tema, String distrik, double kali, boolean tertutup) {
	}

	public static final List<Situs> SEMUA = List.of(
			new Situs("taman_klasik", Tema.AIR, "taman_sastra", 1.2D, false),
			new Situs("aula_emas", Tema.GUNUNG, "puncak_emas", 1.4D, true),
			new Situs("kolam_semedi_darah", Tema.IBLIS, "menara_terlarang", 1.3D, false));

	private static final Map<Tema, List<Predicate<ServerPlayer>>> SYARAT = new ConcurrentHashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		SpiritSites.addSiteProvider(TemaSitus::situs);
	}

	/**
	 * Adds a condition every site of {@code tema} needs (e.g. 38TK-G: the Restu Puncak for the Aula Emas). Several
	 * conditions of one theme must all hold.
	 */
	public static void daftarSyarat(Tema tema, Predicate<ServerPlayer> syarat) {
		if (tema != null && syarat != null) {
			SYARAT.computeIfAbsent(tema, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(syarat);
		}
	}

	/** The site the player sits in now, or null (cheap: one plan lookup, no block reads). */
	public static SpiritSites.@Nullable ExtraSite situs(ServerPlayer player) {
		if (!TemaConfig.inti().aktif || player == null) {
			return null;
		}
		BlockPos feet = player.blockPosition();
		TemaKota.Kota kota = TemaKota.di(player.level(), feet);
		if (kota == null) {
			return null;
		}
		for (Situs s : SEMUA) {
			if (s.tema() != kota.tema() || !TemaConfig.aktif(s.tema())) {
				continue;
			}
			BlockPos centre = kota.distrik(s.distrik());
			if (centre == null || Math.abs(centre.getX() - feet.getX()) > JARI || Math.abs(centre.getZ() - feet.getZ()) > JARI
					|| Math.abs(centre.getY() - feet.getY()) > TINGGI) {
				continue;
			}
			if (!boleh(player, s)) {
				return null;
			}
			return new SpiritSites.ExtraSite("tema." + s.id(), s.kali(), null,
					Component.translatable("kultivator.meditation.site.tema." + s.id()));
		}
		return null;
	}

	private static boolean boleh(ServerPlayer player, Situs s) {
		if (s.tema() == Tema.IBLIS && TemaApi.infami(player) < INFAMI_KOLAM) {
			return false;
		}
		List<Predicate<ServerPlayer>> list = SYARAT.get(s.tema());
		if (list == null || list.isEmpty()) {
			return !s.tertutup();
		}
		for (Predicate<ServerPlayer> p : list) {
			try {
				if (!p.test(player)) {
					return false;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Themed meditation condition of {} failed", s.tema().id(), exception);
				return false;
			}
		}
		return true;
	}

	private TemaSitus() {
	}
}

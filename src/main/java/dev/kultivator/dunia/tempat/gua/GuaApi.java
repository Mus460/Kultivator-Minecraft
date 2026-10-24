package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.dunia.DuniaSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Caves public face (contract C38.GU1, owner 38GU, DESIGN 38.C.4, 38.C.5): whether a player is inside a cave trial
 * (the {@code Sibuk} states {@code tempat.warisan} and {@code tempat.cermin}, the follower veto), the nearest tomb, and
 * the bounded hooks the old systems call (a known treasure-birth place for {@code TreasureBirths}, the den XP factor
 * for {@code SpiritBeasts}). Every answer is neutral while the {@code gua} section is switched off.
 */
public final class GuaApi {

	/** True while {@code player} is inside a cave trial: a tomb's inner room, the Heart Mirror, the hermit's incense. */
	public static boolean dalamUji(ServerPlayer player) {
		if (!GuaConfig.on()) {
			return false;
		}
		if (CerminHati.aktif(player)) {
			return true;
		}
		for (MakamRun run : GuaRuns.all(MakamRun.class)) {
			if (run.level == player.level() && run.menahan(player)) {
				return true;
			}
		}
		PertapaRun hermit = GuaRuns.at(player, PertapaRun.class);
		return hermit != null && hermit.bermeditasi(player);
	}

	/** True while a tomb's inner room holds the player (Sibuk {@code tempat.warisan}). */
	public static boolean dalamMakam(ServerPlayer player) {
		if (!GuaConfig.on()) {
			return false;
		}
		for (MakamRun run : GuaRuns.all(MakamRun.class)) {
			if (run.level == player.level() && run.menahan(player)) {
				return true;
			}
		}
		return false;
	}

	/** The nearest published inheritance tomb to {@code pos} in {@code level} (within 2 000 blocks), or null. */
	public static @Nullable BlockPos makamTerdekat(ServerLevel level, BlockPos pos) {
		List<DuniaSites.Site> sites = DuniaSites.near(level, pos, 2000, GuaAnchors.K_MAKAM);
		return sites.isEmpty() ? null : sites.get(0).pos();
	}

	/** The nearest inheritance tomb {@code player} (or a sect mate) knows in his dimension, or null. */
	public static DuniaSites.@Nullable Site makamTerdekatSitus(ServerPlayer player) {
		DuniaSites.Site best = null;
		double bestD = Double.MAX_VALUE;
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			if (!GuaAnchors.K_MAKAM.equals(site.kind())) {
				continue;
			}
			double d = site.distance(player.level().dimension(), player.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = site;
			}
		}
		return best;
	}

	/**
	 * For {@code TreasureBirths} (38.A #23): at 35 % a known spirit-vein cave or star stage between {@code min} and
	 * {@code max} blocks of {@code focus}; null means "search as before".
	 */
	public static @Nullable BlockPos tempatLahir(ServerLevel level, ServerPlayer focus, double min, double max) {
		return GuaTempat.tempatLahir(level, focus, min, max);
	}

	/** For {@code SpiritBeasts} (38.A #34): ×1.25 spirit XP for a beast fighting in a beast den, else 1. */
	public static double faktorXpBinatang(Mob beast) {
		return GuaTempat.xpBinatang(beast);
	}

	private GuaApi() {
	}
}

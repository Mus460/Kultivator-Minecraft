package dev.kultivator.sekte.langit.main;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.data.Dao;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Qi at height (DESIGN 37.7.1): three extra spiritual sites for members on a standing (not tethered) sky island, and a
 * cleaner vein for them.
 * <ul>
 * <li>{@code sekte_langit}: {@code min(faktorQiMaks 1.25, 1.05 + 0.025 × (tier - 1) + 0.05 with the Grand Formation)}; it
 * stacks with the automatic {@code MOUNTAIN 1.5} (y ≥ 120), so a T9 island gives 1.875.</li>
 * <li>{@code qi_fajar} 1.05 within 2 blocks of a {@code meditasi_<i>} anchor at dawn (23000-1000).</li>
 * <li>{@code bulan_langit} 1.5 (Dao Es) on a full-moon night while it rains below: the vanilla {@code FULL_MOON} fails in
 * the rain, so the two never both apply.</li>
 * </ul>
 * <b>Where the bonus sits (audit 4.4, M4 T6):</b> these are sites, so they join the site product and stay <i>inside</i>
 * {@code Meditation.MULTIPLIER_CAP 3.5} together with the personal formation and the fuel; the sect formation factor G
 * stays outside the cap (max 1.5) and is not touched. A seat already at the cap therefore gains nothing from the site,
 * which the meditation panel's limit line already says. So that the island still helps a capped seat, members on a
 * standing island also get a <b>vein drain factor</b> ({@code Meditation.addDrainFactorProvider}): 0.9, 0.85 from tier 5
 * ("above the clouds the Qi is not shared with the dust of the world"), never below {@code SECT_DRAIN_FLOOR 0.7}.
 * Non-members on the island get only the vanilla-mod MOUNTAIN site.
 */
public final class LangitSites {

	public static final double BASE = 1.05D;
	public static final double PER_TIER = 0.025D;
	public static final double GRAND_BONUS = 0.05D;
	public static final double FAJAR = 1.05D;
	public static final double FAJAR_RANGE = 2.5D;
	public static final double BULAN = 1.5D;
	public static final double DRAIN = 0.9D;
	public static final double DRAIN_HIGH = 0.85D;
	public static final int DRAIN_HIGH_TIER = 5;

	static void register() {
		SpiritSites.addSiteProvider(LangitSites::island);
		SpiritSites.addSiteProvider(LangitSites::dawn);
		SpiritSites.addSiteProvider(LangitSites::moon);
		Meditation.addDrainFactorProvider(LangitSites::drain);
	}

	/** The standing sky hall whose island the member stands on (on the ground, sky above), or null. */
	static @Nullable SkyHallView standingOn(ServerPlayer player) {
		LangitConfig config = LangitConfig.get();
		if (!config.aktif || !config.faktorQi || player.level().dimension() != Level.OVERWORLD || !player.onGround()) {
			return null;
		}
		BlockPos feet = player.blockPosition();
		SkyHallView view = LangitApi.get().at(player.level(), feet);
		if (view == null || view.tethered() || !view.inIsland(feet) || feet.getY() < view.skyFloorY()) {
			return null;
		}
		if (!LangitApi.get().member(view.owner(), player)) {
			return null;
		}
		return view;
	}

	/** The island factor of a hall at {@code tier} (1-9) with or without the Grand Formation. */
	public static double factor(int tier, boolean grand) {
		double cap = LangitConfig.get().faktorQiMaks;
		return Math.min(cap, BASE + PER_TIER * (Math.max(1, Math.min(9, tier)) - 1) + (grand ? GRAND_BONUS : 0.0D));
	}

	private static SpiritSites.@Nullable ExtraSite island(ServerPlayer player) {
		SkyHallView view = standingOn(player);
		if (view == null || !player.level().canSeeSky(player.blockPosition().above())) {
			return null;
		}
		return new SpiritSites.ExtraSite("sekte_langit", factor(view.tier(), view.grandFormation()), null,
				Component.translatable("kultivator.sekte_langit.situs.sekte_langit"));
	}

	private static SpiritSites.@Nullable ExtraSite dawn(ServerPlayer player) {
		SkyHallView view = standingOn(player);
		if (view == null || !dawn(player.level())) {
			return null;
		}
		for (Map.Entry<String, BlockPos> anchor : LangitApi.get().anchors(view.owner()).entrySet()) {
			if (anchor.getKey().startsWith("meditasi_")
					&& Vec3.atCenterOf(anchor.getValue()).distanceToSqr(player.position()) <= FAJAR_RANGE * FAJAR_RANGE + 1.0D) {
				return new SpiritSites.ExtraSite("qi_fajar", FAJAR, null, Component.translatable("kultivator.sekte_langit.situs.qi_fajar"));
			}
		}
		return null;
	}

	private static SpiritSites.@Nullable ExtraSite moon(ServerPlayer player) {
		SkyHallView view = standingOn(player);
		if (view == null) {
			return null;
		}
		ServerLevel level = player.level();
		BlockPos head = player.blockPosition().above();
		if (!level.isDarkOutside() || !level.isRaining() || !level.canSeeSky(head)) {
			return null;
		}
		if (level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, head) != MoonPhase.FULL_MOON) {
			return null;
		}
		return new SpiritSites.ExtraSite("bulan_langit", BULAN, Dao.ICE, Component.translatable("kultivator.sekte_langit.situs.bulan_langit"));
	}

	private static double drain(ServerPlayer player) {
		SkyHallView view = standingOn(player);
		if (view == null) {
			return 1.0D;
		}
		return view.tier() >= DRAIN_HIGH_TIER ? DRAIN_HIGH : DRAIN;
	}

	/** World time of day within the dawn window 23000-1000. */
	static boolean dawn(Level level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= 23000L || time < 1000L;
	}

	/** Dusk window 11500-13000 (the watching disciples, the envoy's light). */
	static boolean dusk(Level level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= 11500L && time < 13000L;
	}

	private LangitSites() {
	}
}

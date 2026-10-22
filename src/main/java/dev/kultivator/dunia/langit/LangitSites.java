package dev.kultivator.dunia.langit;

import dev.kultivator.cultivation.Enlightenment;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.util.Advancements;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The meditation side of module L (DESIGN 34.5.8, 34.5.10) through the one hook {@code SpiritSites.addSiteProvider}
 * (contract C34.L1): two providers, both outdoors only ({@code canSeeSky}), multiplied with the enum sites before
 * {@code SpiritSites.CAP} 4.0 and so still under {@code Meditation.MULTIPLIER_CAP} 3.5:
 * <ul>
 * <li>the spirit weather: Kabut Qi Tipis ×{@code thinFactor} 1.2, Kabut Qi Pekat ×{@code denseFactor} 1.5, Fajar Ungu
 * ×{@code dawnFactor} 1.6;</li>
 * <li>the sky event: Hujan Bintang ×1.3 under the meteor shower, Gerhana Darah ×1.3 for Shadow Dao, Bulan Kembar ×1.5
 * when the full-moon site does not already count (the full moon "counts all night").</li>
 * </ul>
 * A Dao of an extra site is nourished as an enum site's is (the same 0.004 per second while meditating). Bulan Kembar
 * also gives Ice and Shadow Dao ×1.5 comprehension; Fajar Ungu doubles the Dao insight chance while facing east.
 */
public final class LangitSites {

	/** Comprehension per second a matching site teaches (the value of {@code Meditation.SITE_COMPREHENSION}). */
	public static final double SITE_COMPREHENSION = 0.004D;
	public static final double METEOR_FACTOR = 1.3D;
	public static final double ECLIPSE_FACTOR = 1.3D;
	public static final double TWIN_FACTOR = 1.5D;
	/** Facing east: the horizontal look vector's x at least this. */
	private static final double EAST = 0.6D;

	static void init() {
		SpiritSites.addSiteProvider(LangitSites::weatherSite);
		SpiritSites.addSiteProvider(LangitSites::skySite);
	}

	/** The spirit weather site of the player now, or null. */
	static SpiritSites.@Nullable ExtraSite weatherSite(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!SpiritWeathers.on(level) || !outdoors(player)) {
			return null;
		}
		SpiritWeather weather = SpiritWeathers.at(level, player.blockPosition());
		return switch (weather) {
			case THIN_MIST -> new SpiritSites.ExtraSite("kabut_tipis", SpiritWeathers.factor(weather), null,
					Component.translatable("kultivator.langit.site.thin_mist"));
			case DENSE_MIST -> new SpiritSites.ExtraSite("kabut_pekat", SpiritWeathers.factor(weather), null,
					Component.translatable("kultivator.langit.site.dense_mist"));
			case PURPLE_DAWN -> new SpiritSites.ExtraSite("fajar_ungu", SpiritWeathers.factor(weather), null,
					Component.translatable("kultivator.langit.site.purple_dawn"));
			case CLEAR -> null;
		};
	}

	/** The sky event site of the player now, or null. */
	static SpiritSites.@Nullable ExtraSite skySite(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!DuniaSpine.enabled() || !outdoors(player)) {
			return null;
		}
		SkyKind kind = SkyEvents.active(level);
		if (kind == null) {
			return null;
		}
		return switch (kind) {
			case METEOR_SHOWER -> new SpiritSites.ExtraSite("hujan_bintang", METEOR_FACTOR, Dao.SPACE,
					Component.translatable("kultivator.langit.site.meteor_shower"));
			case BLOOD_ECLIPSE -> Cultivators.get(player).dao() == Dao.SHADOW
					? new SpiritSites.ExtraSite("gerhana_darah", ECLIPSE_FACTOR, Dao.SHADOW, Component.translatable("kultivator.langit.site.blood_eclipse"))
					: null;
			case TWIN_MOONS -> level.isDarkOutside() && !level.isRaining() && !realFullMoon(level, player.blockPosition().above())
					? new SpiritSites.ExtraSite("bulan_kembar", TWIN_FACTOR, Dao.ICE, Component.translatable("kultivator.langit.site.twin_moons"))
					: null;
		};
	}

	/** {@code SkyService.siteFactor}: the product of L's extra sites for the player now (1 for none). */
	static double factor(ServerPlayer player) {
		double product = 1.0D;
		SpiritSites.ExtraSite weather = weatherSite(player);
		if (weather != null) {
			product *= weather.multiplier();
		}
		SpiritSites.ExtraSite sky = skySite(player);
		if (sky != null) {
			product *= sky.multiplier();
		}
		return Math.min(SpiritSites.CAP, product);
	}

	/**
	 * Every 20 ticks from {@link Langit}: for each meditating player, the Dao of L's extra sites, the Bulan Kembar bonus,
	 * the Fajar Ungu insight boost and the {@code dunia/qi_mist} advancement.
	 */
	static void tick(MinecraftServer server) {
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (!Meditation.isMeditating(player) || !outdoors(player)) {
				continue;
			}
			ServerLevel level = player.level();
			Dao dao = Cultivators.get(player).dao();
			SpiritSites.ExtraSite weather = weatherSite(player);
			SpiritSites.ExtraSite sky = skySite(player);
			if (dao != null) {
				if (weather != null && weather.dao() == dao) {
					Comprehension.add(player, dao, SITE_COMPREHENSION);
				}
				if (sky != null && sky.dao() == dao) {
					Comprehension.add(player, dao, SITE_COMPREHENSION);
				}
				if (SkyEvents.is(level, SkyKind.TWIN_MOONS) && (dao == Dao.ICE || dao == Dao.SHADOW)) {
					// ×1.5 of what a matching site teaches
					Comprehension.add(player, dao, SITE_COMPREHENSION * 0.5D);
				}
			}
			if (weather != null) {
				Advancements.grant(player, "dunia/qi_mist");
				if (weather.id().equals("fajar_ungu") && facingEast(player)) {
					Enlightenment.boostChance(player, 2.0D, 40);
					// Yin Xi at the pass: the purple Qi met face to face
					Advancements.grant(player, "dunia/purple_dawn");
				}
			}
		}
	}

	static boolean outdoors(ServerPlayer player) {
		return player.level().canSeeSky(player.blockPosition().above());
	}

	static boolean facingEast(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		return horizontal > 1.0E-3D && look.x / horizontal >= EAST;
	}

	/** The real full moon at {@code pos} (the enum site already counts then). */
	private static boolean realFullMoon(ServerLevel level, BlockPos pos) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos) == MoonPhase.FULL_MOON;
	}

	private LangitSites() {
	}
}

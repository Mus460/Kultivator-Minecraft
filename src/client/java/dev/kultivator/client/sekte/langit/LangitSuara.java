package dev.kultivator.client.sekte.langit;

import dev.kultivator.sekte.langit.api.LangitIds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The sounds of the sky sect (DESIGN 37.6.6), all vanilla events played locally on this client:
 * <table>
 * <caption>ambient (at most 4 per 5 s, switch "Suara ambien langit")</caption>
 * <tr><td>sky wind</td><td>{@code ELYTRA_FLYING} 0.08-0.18 / 0.5, every 120-200 ticks within 6 blocks of the rim or on a
 * bridge (out past the island's edge)</td></tr>
 * <tr><td>gate bell</td><td>{@code BELL_RESONATE} 0.6 / 0.8 once at sunrise (time 0) from the Nine Heavens gate or the
 * hall</td></tr>
 * <tr><td>wind chimes</td><td>{@code AMETHYST_BLOCK_CHIME} 0.3 / 1.0-1.4, 1 in 200 ticks within 12 blocks of a pavilion
 * roof (gate, meditation pavilions, the hall; tier 4+, the wind bells of the T4 ornament)</td></tr>
 * <tr><td>cranes</td><td>{@code PARROT_FLY} 0.3 / 0.6 when a crane is within 24 blocks</td></tr>
 * <tr><td>falls</td><td>{@code WEATHER_RAIN_ABOVE} 0.2 / 0.7 every 40 ticks within 20 blocks of a mist fall</td></tr>
 * </table>
 * One-off: Cloud Transfer {@code WIND_CHARGE_BURST} 0.7 / 1.2 on arrival; Cloud Net {@code BREEZE_IDLE_AIR} 0.8 / 1.0;
 * Ascension {@code BELL_RESONATE} + {@code AMETHYST_BLOCK_CHIME} at 1.0; a pirate breach a low wind burst; a tribulation
 * below a muffled {@code LIGHTNING_BOLT_THUNDER} (0.35 / 0.6). The vanilla categories are kept (AMBIENT, WEATHER,
 * NEUTRAL, BLOCKS), so the player's volume sliders rule them.
 */
final class LangitSuara {

	private static final int WINDOW = 100;
	private static final int MAX_PER_WINDOW = 4;

	private static int windowTicks;
	private static int played;
	private static int nextWind = 140;
	private static int nextFall;
	private static int nextCrane;
	private static long bellDay = Long.MIN_VALUE;

	static void tick(ClientLevel level, double x, double y, double z, LangitSkyState.Hall hall, long dayTime, long day) {
		if (++windowTicks >= WINDOW) {
			windowTicks = 0;
			played = 0;
		}
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.SUARA_AMBIEN) || hall.tethered()) {
			return;
		}
		RandomSource random = level.getRandom();
		boolean onIsland = hall.onIsland(x, y, z);
		// the gate bell at sunrise
		if (onIsland && dayTime < 40L && bellDay != day) {
			bellDay = day;
			BlockPos bell = hall.gate != null ? hall.gate : hall.origin;
			play(level, bell, SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 0.6F, 0.8F, true);
		}
		// the sky wind at the rim
		if (--nextWind <= 0) {
			nextWind = 120 + random.nextInt(81);
			double dx = x - hall.centreX();
			double dz = z - hall.centreZ();
			double fromCentre = Math.sqrt(dx * dx + dz * dz);
			if (onIsland && fromCentre > hall.radius() - 6.0D) {
				play(level, BlockPos.containing(x, y + 1.0D, z), SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.08F + random.nextFloat() * 0.1F,
						0.5F, false);
			}
		}
		// wind chimes under the pavilion eaves
		if (onIsland && hall.tier >= 4 && random.nextInt(200) == 0) {
			BlockPos chime = nearestRoof(hall, x, y, z);
			if (chime != null) {
				play(level, chime, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.3F, 1.0F + random.nextFloat() * 0.4F, false);
			}
		}
		// the falls
		if (--nextFall <= 0) {
			nextFall = 40;
			if (LangitClientConfig.on(LangitClientConfig.Toggle.AIR_TERJUN)) {
				for (LangitSkyState.Fall fall : hall.falls) {
					if (fall.pos().distToCenterSqr(x, y, z) <= 20.0D * 20.0D) {
						BlockPos at = fall.pos().offset(fall.dx() * 2, -4, fall.dz() * 2);
						play(level, at, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.2F, 0.7F, false);
						break;
					}
				}
			}
		}
		// the cranes' wing beats
		if (--nextCrane <= 0) {
			nextCrane = 50 + random.nextInt(40);
			if (KawananBangau.nearest <= 24.0F) {
				play(level, BlockPos.containing(KawananBangau.nearX, KawananBangau.nearY, KawananBangau.nearZ), SoundEvents.PARROT_FLY,
						SoundSource.NEUTRAL, 0.3F, 0.6F, false);
			}
		}
	}

	/** The one-off sound of an effect op. */
	static void fx(ClientLevel level, int op, BlockPos a, BlockPos b) {
		switch (op) {
			case LangitIds.FX_PINDAH -> {
				level.playLocalSound(b.getX() + 0.5D, b.getY() + 0.5D, b.getZ() + 0.5D, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.BLOCKS,
						0.7F, 1.2F, false);
				level.playLocalSound(a.getX() + 0.5D, a.getY() + 0.5D, a.getZ() + 0.5D, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.BLOCKS,
						0.5F, 1.3F, false);
			}
			case LangitIds.FX_TANGKAP -> level.playLocalSound(b.getX() + 0.5D, b.getY() + 0.5D, b.getZ() + 0.5D, SoundEvents.BREEZE_IDLE_AIR,
					SoundSource.NEUTRAL, 0.8F, 1.0F, false);
			case LangitIds.FX_NAIK -> {
				level.playLocalSound(a.getX() + 0.5D, a.getY() + 0.5D, a.getZ() + 0.5D, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F, false);
				level.playLocalSound(a.getX() + 0.5D, a.getY() + 1.5D, a.getZ() + 0.5D, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F,
						false);
			}
			case LangitIds.FX_ARRIVE, LangitIds.FX_FERI -> level.playLocalSound(a.getX() + 0.5D, a.getY() + 0.5D, a.getZ() + 0.5D,
					SoundEvents.BREEZE_IDLE_AIR, SoundSource.NEUTRAL, 0.35F, 1.4F, false);
			case LangitIds.FX_BAJAK -> level.playLocalSound(a.getX() + 0.5D, a.getY() + 0.5D, a.getZ() + 0.5D, SoundEvents.WIND_CHARGE_BURST.value(),
					SoundSource.HOSTILE, 1.0F, 0.55F, false);
			case LangitIds.FX_SENGSARA_BAWAH -> level.playLocalSound(a.getX() + 0.5D, a.getY() + 30.5D, a.getZ() + 0.5D,
					SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.35F, 0.6F, false);
			default -> {
			}
		}
	}

	private static BlockPos nearestRoof(LangitSkyState.Hall hall, double x, double y, double z) {
		BlockPos best = null;
		double bestD = 12.0D * 12.0D;
		if (hall.gate != null && hall.gate.distToCenterSqr(x, y, z) < bestD) {
			best = hall.gate.above(8);
			bestD = hall.gate.distToCenterSqr(x, y, z);
		}
		for (BlockPos seat : hall.seats) {
			double d = seat.distToCenterSqr(x, y, z);
			if (d < bestD) {
				best = seat.above(4);
				bestD = d;
			}
		}
		if (hall.origin.distToCenterSqr(x, y, z) < bestD) {
			best = hall.origin.above(6);
		}
		return best;
	}

	private static void play(ClientLevel level, BlockPos at, SoundEvent sound, SoundSource source, float volume, float pitch, boolean always) {
		if (!always && played >= MAX_PER_WINDOW) {
			return;
		}
		played++;
		level.playLocalSound(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, sound, source, volume, pitch, false);
	}

	static void clear() {
		windowTicks = 0;
		played = 0;
		bellDay = Long.MIN_VALUE;
	}

	private LangitSuara() {
	}
}

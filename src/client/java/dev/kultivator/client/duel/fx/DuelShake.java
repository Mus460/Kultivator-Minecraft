package dev.kultivator.client.duel.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.client.epic.screen.ImpactFrame;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.technique.spec.dsl.Dampak;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Camera shake, impact frames and screen flashes of the duel (DESIGN 35.4.7), through the shared 6c / Phase 10 helpers so
 * every safety rule holds: shake ≤ 2.5° × the "Guncangan" setting × vanilla damage tilt and clamped per frame, nothing
 * with "Kurangi gerak"; impact frames only when "Bingkai dampak" is not Mati and the shared luminance gate allows
 * (500 ms apart, never with "Hide Lightning Flashes"), α × 0.5 for onlookers; flashes through {@link ScreenFlash}
 * ("Kilatan layar").
 */
public final class DuelShake {

	private static long lastBindShake;

	/** A plain shake of {@code intensity} for {@code ticks} at {@code at} (full within {@code radius}). */
	public static void shake(float intensity, int ticks, Vec3 at, float radius, boolean climax) {
		if (EffectQuality.reduceMotion()) {
			return;
		}
		CameraShake.add(intensity, ticks, at, radius, climax);
	}

	/** The table of 35.4.7 by outcome; {@code involved} = the local player is one of the pair. */
	public static void outcome(int outcome, Vec3 at, boolean involved, boolean selfHit, boolean decisive) {
		switch (outcome) {
			case DuelCodes.CLASH, DuelCodes.CLASH_MENANG, DuelCodes.BIND -> shake(0.12F, 4, at, 8.0F, false);
			case DuelCodes.PERFECT, DuelCodes.MIKIRI -> shake(0.22F, 5, at, 8.0F, false);
			case DuelCodes.GUARD_BREAK, DuelCodes.POSTURE_BREAK, DuelCodes.BIND_MENANG_A, DuelCodes.BIND_MENANG_B ->
					shake(0.45F, 8, at, 10.0F, false);
			case DuelCodes.DEATHBLOW -> shake(0.6F, 10, at, 12.0F, true);
			case DuelCodes.HIT -> {
				if (selfHit) {
					shake(0.18F, 4, at, 8.0F, false);
				} else if (decisive) {
					shake(0.6F, 10, at, 12.0F, true);
				}
			}
			default -> {
			}
		}
	}

	/** The low rumble of a blade lock: 0.05, renewed every 4 ticks while the lock runs. */
	public static void bindRumble(Vec3 at, long tick) {
		if (tick - lastBindShake >= 4) {
			lastBindShake = tick;
			shake(0.05F, 5, at, 8.0F, false);
		}
	}

	/**
	 * The impact frame of a posture break, a guard break or a deathblow (35.4.7): Lembut one tint of the attacker's colour
	 * (white for a deathblow), Penuh the three frames; α × 0.5 for onlookers. False when nothing showed.
	 */
	public static boolean impactFrame(boolean deathblow, int rgb, boolean involved, int seed) {
		EpicSettings.ImpactFrames mode = EpicSettings.impactFrames();
		if (mode == EpicSettings.ImpactFrames.MATI || Minecraft.getInstance().player == null) {
			return false;
		}
		boolean full = mode == EpicSettings.ImpactFrames.PENUH;
		int colour = rgb & 0xFFFFFF;
		float alpha = involved ? 1.0F : 0.5F;
		if (deathblow) {
			return full ? ImpactFrame.play(Dampak.INV, Dampak.BLACK, colour, true, alpha, true, seed)
					: ImpactFrame.play(0xFFFFFF, Dampak.NO_FRAME, Dampak.NO_FRAME, false, alpha, true, seed);
		}
		return full ? ImpactFrame.play(Dampak.INV, colour, colour, true, alpha, true, seed)
				: ImpactFrame.play(colour, Dampak.NO_FRAME, Dampak.NO_FRAME, false, alpha, true, seed);
	}

	/** One's own perfect deflect: a brief warm flash (through the luminance gate; nothing with "Kilatan layar" off). */
	public static void perfectFlash() {
		if (EffectQuality.flash() && !EffectQuality.reduceMotion() && EpicScreen.tryLuminance()) {
			ScreenFlash.add(0xFFE7A0, 0.12F, 3);
		}
	}

	private DuelShake() {
	}
}

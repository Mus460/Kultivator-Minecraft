package dev.kultivator.dunia.kapal;

import net.minecraft.util.Mth;

/** Section {@code kapal} of {@code config/kultivator-dunia.json} (DESIGN 34.5.15). Public fields: Gson reads them. */
public final class KapalConfig {

	/** Master switch of the flying ships (false: no launch, deployed ships set down and only "Simpan" works). */
	public boolean ships = true;
	/** Speed cap in blocks per second for every class (8-24). */
	public int maxSpeed = 20;
	/** Lompatan Awan on long voyages. */
	public boolean cloudLeap = true;
	/** Length of one cloud leap in blocks (256-1024). */
	public int leapDistance = 512;
	/** Sky pirates on long voyages. */
	public boolean pirates = true;
	/** Chance of a Bajak Langit ambush on a voyage longer than 2 000 blocks (0-1). */
	public double pirateChance = 0.15D;
	/** Fuel use multiplier (0.1-5). */
	public double fuelScale = 1.0D;
	/** Deck colliders on a docked ship (players can walk on its deck). */
	public boolean deckWalk = true;

	KapalConfig clamp() {
		maxSpeed = Mth.clamp(maxSpeed, 8, 24);
		leapDistance = Mth.clamp(leapDistance, 256, 1024);
		pirateChance = Double.isFinite(pirateChance) ? Mth.clamp(pirateChance, 0.0D, 1.0D) : 0.15D;
		fuelScale = Double.isFinite(fuelScale) ? Mth.clamp(fuelScale, 0.1D, 5.0D) : 1.0D;
		return this;
	}
}

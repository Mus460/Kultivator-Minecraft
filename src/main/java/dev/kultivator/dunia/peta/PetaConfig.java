package dev.kultivator.dunia.peta;

import net.minecraft.util.Mth;

/** Section {@code peta} of {@code config/kultivator-dunia.json} (DESIGN 34.5.16). Public fields: Gson reads them. */
public final class PetaConfig {

	/** Master switch of the world map (screen, sampling, fog). */
	public boolean map = true;
	/** Blocks per map cell (32-256; a change forgets the sampled grid and the fog). */
	public int cellSize = 64;
	/** Biome samples per server tick at most (16-512; also capped at 1.5 ms per tick). */
	public int samplesPerTick = 128;
	/** Waypoints per player (0-64). */
	public int waypoints = 32;
	/** NPC sect domains drawn on the map (0-16). */
	public int npcDomains = 8;
	/** Sect mates see each other's explored map. */
	public boolean shareWithSect = true;

	PetaConfig clamp() {
		cellSize = Mth.clamp(cellSize, 32, 256);
		samplesPerTick = Mth.clamp(samplesPerTick, 16, 512);
		waypoints = Mth.clamp(waypoints, 0, 64);
		npcDomains = Mth.clamp(npcDomains, 0, 16);
		return this;
	}
}

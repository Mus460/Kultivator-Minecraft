package dev.kultivator.dunia.kota;

import net.minecraft.util.Mth;

/**
 * Section {@code kota} of {@code config/kultivator-dunia.json} (DESIGN 34.5.1-34.5.4): every switch and number of module
 * K, clamped on load. Read through {@link Kota#config()}.
 */
public final class KotaConfig {

	// 34.5.1 Kota Kultivator
	/** Registers the {@code kultivator:dunia_kota} world generation pack (towns and the Immortal City in new chunks). */
	public boolean worldgen = true;
	/** The towns' NPCs, shops and services. */
	public boolean enabled = true;
	public int npcPerTown = 10;
	public boolean auction = true;
	public int auctionRivals = 4;
	public boolean blackMarket = true;
	public double raidChance = 0.08D;
	public boolean bounties = true;
	public int bountiesPerDay = 3;
	public double teaBonus = 0.10D;
	public double priceScale = 1.0D;
	// 34.5.2 Kultivator Pengembara
	public boolean wanderers = true;
	public double wandererChance = 1.0D;
	public int discussionsPerDay = 3;
	public boolean companion = true;
	// 34.5.3 Saingan
	public boolean rivals = true;
	public int rivalGroups = 6;
	public int racePhysicalRadius = 128;
	public boolean rivalAmbush = true;
	public int ambushPerDay = 1;
	// 34.5.4 Kota Abadi
	public boolean immortalCity = true;
	public boolean lectures = true;
	public int peachDays = 3;

	KotaConfig clamp() {
		npcPerTown = Mth.clamp(npcPerTown, 4, 14);
		auctionRivals = Mth.clamp(auctionRivals, 0, 6);
		raidChance = Mth.clamp(raidChance, 0.0D, 0.3D);
		bountiesPerDay = Mth.clamp(bountiesPerDay, 0, 6);
		teaBonus = Mth.clamp(teaBonus, 0.0D, 0.2D);
		priceScale = Mth.clamp(priceScale, 0.5D, 2.0D);
		wandererChance = Mth.clamp(wandererChance, 0.0D, 3.0D);
		discussionsPerDay = Mth.clamp(discussionsPerDay, 0, 10);
		rivalGroups = Mth.clamp(rivalGroups, 3, 8);
		racePhysicalRadius = Mth.clamp(racePhysicalRadius, 48, 256);
		ambushPerDay = Mth.clamp(ambushPerDay, 0, 3);
		peachDays = Mth.clamp(peachDays, 1, 14);
		return this;
	}
}

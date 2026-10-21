package dev.kultivator.musuh;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One live sect raid (memory only, DESIGN 25.14): its waves and spawn queue, the raiders alive, the breach meter "Segel
 * Penjarah", who helped, and the two vanilla boss bars (25.10). {@link SectRaids} drives it once a second.
 */
final class RaidState {

	/** Meter per second for a channelling raider of tier 1-4 (DESIGN 25.4.5). */
	static final double[] METER_RATE = {0.0D, 1.0D, 2.0D, 4.0D, 6.0D};

	final UUID owner;
	final ServerLevel level;
	final BlockPos stele;
	final double radius;
	final int hallLevel;
	final int waves;
	final boolean finalWave;
	final int sect;
	final int raidLevel;
	final double[] angles;
	final boolean test;
	final long startedAt;

	int wave;
	int waveSize;
	final ArrayDeque<Integer> queue = new ArrayDeque<>();
	final Set<RogueCultivatorEntity> alive = new LinkedHashSet<>();
	double meter;
	double maxMeter;
	long clearedAt = -1L;
	long lowSince = -1L;
	long absentSince = -1L;
	int spawnFails;
	int spawnIndex;
	/** Raiders that arrived in this raid (all waves). */
	int spawned;
	/** Queued raiders dropped because they found no room to arrive (the raid is then not flawless). */
	int lost;
	int elders;
	int patriarchs;
	boolean discipleHelped;
	final Set<UUID> helpers = new HashSet<>();
	final ServerBossEvent bar;
	final ServerBossEvent seal;

	RaidState(UUID owner, ServerLevel level, BlockPos stele, double radius, int hallLevel, int waves, boolean finalWave, int sect, int raidLevel,
			double[] angles, boolean test, long now) {
		this.owner = owner;
		this.level = level;
		this.stele = stele;
		this.radius = radius;
		this.hallLevel = hallLevel;
		this.waves = waves;
		this.finalWave = finalWave;
		this.sect = sect;
		this.raidLevel = raidLevel;
		this.angles = angles;
		this.test = test;
		this.startedAt = now;
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.musuh.raid.bar", 1, waves, 0),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
		this.seal = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.musuh.raid.seal_bar", 0),
				BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
		this.seal.setProgress(0.0F);
	}

	Vec3 centre() {
		return Vec3.atBottomCenterOf(stele);
	}

	/** The tiers of wave {@code index} (1-based); the last wave is the Patriarch's when {@link #finalWave}. */
	int[] tiersOf(int index) {
		if (index >= waves && finalWave) {
			return new int[]{4, 2, 2};
		}
		return switch (index) {
			case 1 -> new int[]{1, 1, 1, 1};
			case 2 -> new int[]{1, 1, 1, 2, 2};
			case 3 -> hallLevel >= 4 ? new int[]{2, 2, 3} : new int[]{2, 2, 1};
			default -> new int[]{2, 2, 2, 3};
		};
	}

	/** Starts the next wave: fills the spawn queue. */
	void nextWave() {
		wave++;
		queue.clear();
		for (int tier : tiersOf(wave)) {
			queue.add(tier);
		}
		waveSize = queue.size();
		clearedAt = -1L;
		lowSince = -1L;
		spawnFails = 0;
	}

	boolean contains(@Nullable RogueCultivatorEntity mob) {
		return mob != null && alive.contains(mob);
	}

	/** Raiders still to fight in this wave (alive plus queued). */
	int remaining() {
		return alive.size() + queue.size();
	}

	/** The raid bar's progress: waves done plus the share of this wave beaten. */
	float progress() {
		double inWave = waveSize <= 0 ? 1.0D : 1.0D - (double) remaining() / waveSize;
		return (float) Math.max(0.0D, Math.min(1.0D, (wave - 1 + inWave) / Math.max(1, waves)));
	}

	void clearBars() {
		bar.removeAllPlayers();
		seal.removeAllPlayers();
	}
}

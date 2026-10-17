package dev.kultivator.disciple.power;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.SectRanks;

import java.util.UUID;

/**
 * Formasi Tujuh Peri (DESIGN 20.3.16; Against the Gods ch. 739, the Frozen Cloud Seven Fairies, the sect's elite seven):
 * when 7 or more Inti+ disciples of one master fight within 16 blocks of each other, each deals +10% damage. Checked once
 * a second per fighting Inti+ disciple over the master's loaded disciples (a short list), kept for 2 s.
 */
final class SevenFairies {

	public static final int NEEDED = 7;
	public static final double RANGE = 16.0D;
	public static final double DAMAGE = 1.1D;
	/** Inti (Core) rank and above. */
	public static final int MIN_RANK = 3;

	static void register() {
		DiscipleHooks.addDamageFactor((entity, target, technique) -> PowerCore.powered(entity)
				&& DisciplePowers.live(entity).fairiesUntil > PowerCore.now(entity) ? DAMAGE : 1.0D);
	}

	static void tick(DiscipleEntity entity, DisciplePowers.Live live) {
		UUID owner = entity.ownerId();
		if (owner == null || !fighting(entity) || SectRanks.rank(entity.data()) < MIN_RANK) {
			return;
		}
		int count = 0;
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (other.level() == entity.level() && other.role() == DiscipleEntity.Role.DISCIPLE && fighting(other)
					&& other.distanceToSqr(entity) <= RANGE * RANGE && SectRanks.rank(other.data()) >= MIN_RANK) {
				count++;
			}
		}
		if (count >= NEEDED) {
			live.fairiesUntil = PowerCore.now(entity) + 40L;
		}
	}

	private static boolean fighting(DiscipleEntity entity) {
		return (entity.syncedFlags() & DiscipleEntity.FLAG_FIGHTING) != 0;
	}

	private SevenFairies() {
	}
}

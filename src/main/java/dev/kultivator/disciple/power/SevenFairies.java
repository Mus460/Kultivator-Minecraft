package dev.kultivator.disciple.power;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.SectRanks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	/** Per master: the link stays up until this tick; a new link (edge) shows the seven-colour ribbon once. */
	private static final Map<UUID, Long> LINKED = new HashMap<>();

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
		List<DiscipleEntity> seven = new ArrayList<>();
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (other.level() == entity.level() && other.role() == DiscipleEntity.Role.DISCIPLE && fighting(other)
					&& other.distanceToSqr(entity) <= RANGE * RANGE && SectRanks.rank(other.data()) >= MIN_RANK) {
				count++;
				if (seven.size() < NEEDED) {
					seven.add(other);
				}
			}
		}
		if (count >= NEEDED) {
			long now = PowerCore.now(entity);
			live.fairiesUntil = now + 40L;
			Long until = LINKED.get(owner);
			LINKED.put(owner, now + 40L);
			if ((until == null || until <= now) && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
				// the edge: one packet per fairy (DESIGN 27.6), the client ties the seven ids into one light ribbon
				net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.ZERO;
				for (DiscipleEntity fairy : seven) {
					centre = centre.add(fairy.position());
				}
				centre = centre.scale(1.0D / Math.max(1, seven.size()));
				for (int i = 0; i < seven.size(); i++) {
					DiscipleEntity fairy = seven.get(i);
					PowerCore.fx(level, fairy.position(), centre, PowerIds.FX_FAIRIES, fairy.getId(), i);
				}
				PowerCore.sound(level, centre, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.0F);
				net.minecraft.server.level.ServerPlayer master = entity.owner();
				if (master != null) {
					dev.kultivator.util.Msg.actionBar(master, PowerCore.key("fairies.linked").withColor(0xFFC8F0));
				}
			}
		}
	}

	private static boolean fighting(DiscipleEntity entity) {
		return (entity.syncedFlags() & DiscipleEntity.FLAG_FIGHTING) != 0;
	}

	private SevenFairies() {
	}
}

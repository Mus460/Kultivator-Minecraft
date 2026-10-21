package dev.kultivator.dunia.kota;

import dev.kultivator.data.Dao;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.MusuhFxIds;
import dev.kultivator.sect.event.RivalSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The NPCs' techniques (DESIGN 34.5.2, 34.5.3): the public 6c rival primitives, nothing new. The qi line every 6 s and,
 * from tier 2, the element burst every 10 s, both with the rogue damage of DESIGN 25.4.2: {@code U × (0.5 + 0.25 tier)},
 * {@code U = 3 + 2R + 0.5 stage}. Every hit is filtered by {@link NpcAi#mayHit}; entities only, never a block. The skill
 * colour follows the NPC's Dao (the group colour for rivals).
 */
final class NpcSkills {

	static final int LINE_COOLDOWN = 120;
	static final int BURST_COOLDOWN = 200;

	static double damage(KultivatorNpc npc) {
		int tier = Math.max(1, Math.min(4, npc.memory().getIntOr("tier", npc.role().tier)));
		double u = 3.0D + 2.0D * npc.realm() + 0.5D * npc.stage();
		return u * (0.5D + 0.25D * tier);
	}

	static int color(KultivatorNpc npc) {
		if (!npc.group().isEmpty()) {
			return Saingan.colorOf(npc.group());
		}
		Dao dao = npc.dao();
		return dao == null ? 0x9FD8FF : daoColor(dao);
	}

	/** The Dao's colour (the rival skills' tint). */
	static int daoColor(Dao dao) {
		return dao.color();
	}

	static void use(ServerLevel level, KultivatorNpc npc, LivingEntity target, long now) {
		if (npc.distanceTo(target) > RivalSkills.LINE_RANGE || !npc.hasLineOfSight(target)) {
			return;
		}
		int tier = Math.max(1, Math.min(4, npc.memory().getIntOr("tier", npc.role().tier)));
		long nextBurst = npc.memory().getLongOr("nb", now + 60L);
		long nextLine = npc.memory().getLongOr("nl", now + 20L);
		if (!npc.memory().contains("nb")) {
			npc.memory().putLong("nb", nextBurst);
			npc.memory().putLong("nl", nextLine);
		}
		if (tier >= 2 && now >= nextBurst) {
			npc.memory().putLong("nb", now + BURST_COOLDOWN + npc.getRandom().nextInt(40));
			RivalSkills.elementBurst(level, npc, target, damage(npc), color(npc), each -> NpcAi.mayHit(npc, each));
			return;
		}
		if (now >= nextLine) {
			npc.memory().putLong("nl", now + LINE_COOLDOWN + npc.getRandom().nextInt(30));
			RivalSkills.qiLine(level, npc, target, damage(npc), color(npc), each -> NpcAi.mayHit(npc, each));
		}
	}

	/** The cloud-step puff of the rogue module where an NPC leaves. */
	static void vanishFx(ServerLevel level, KultivatorNpc npc) {
		Vec3 at = npc.position();
		Fx.send(level, at.add(0.0D, 1.0D, 0.0D), FxType.MUSUH, MusuhFxIds.VANISH, color(npc), 1.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.6F, 1.0F);
	}

	/** The dark swirl of the rogue module where an NPC arrives (wanderers, rivals). */
	static void arriveFx(ServerLevel level, KultivatorNpc npc) {
		Fx.send(level, npc.position(), FxType.MUSUH, MusuhFxIds.ARRIVE, color(npc), Math.max(1, npc.memory().getIntOr("tier", npc.role().tier)));
	}

	private NpcSkills() {
	}
}
